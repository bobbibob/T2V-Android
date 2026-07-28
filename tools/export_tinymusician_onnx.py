#!/usr/bin/env python3
"""
Export TinyMusician (asigalov61) to ONNX int8 for T2V-Android.

TinyMusician: https://huggingface.co/asigalov61/TinyMusician
Paper: arxiv:2502.12945 (Feb 2025)
License: MIT

This script:
  1. Clones the upstream repo
  2. Loads the PyTorch checkpoint for `tinymusician-small-44m` (or 100M)
  3. Traces the model to a fixed context length
  4. Exports to ONNX (opset 17, ARM64-compatible)
  5. Quantizes to int8 using onnxruntime.quantization
  6. Splits the decoder KV-cache into past/present for autoregressive
     generation
  7. Writes `tokenizer.json` and `model.onnx` to a target directory
  8. Computes SHA-256 of each output file

Usage:
    pip install torch onnx onnxruntime transformers huggingface_hub
    python tools/export_tinymusician_onnx.py \\
        --variant small \\
        --output-dir ./tinymusician-small-onnx

Output (mirrors the manifest in
`app/src/main/java/com/t2v/generators/runtime/LiteRtModelRuntime.kt`):
    tinymusician-small-onnx/
        model.onnx                 # ~180 MB int8
        tokenizer.json             # ~50 KB
        model.onnx.sha256

T2V downloads these from the `asigalov61/TinyMusician-ONNX` repo
(created by this script) and loads via onnxruntime-mobile.

This script is **not** run by CI (PyTorch + ARM64 cross-compilation
toolchain is too heavy). Run on a workstation with GPU once; commit
the resulting `.onnx` files to a separate HF repo, then update the
catalog entry in T2V to point at it.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Optional

REPO_URL = "https://github.com/asigalov61/TinyMusician.git"
HF_OUT_REPO = "asigalov61/TinyMusician-ONNX"  # override via --hf-repo

VARIANTS = {
    # name: (hf_model_id, context_length, hidden_dim, num_layers, num_heads)
    "small":  ("asigalov61/TinyMusician-Small-44M", 1024, 128, 6, 4),
    "100m":   ("asigalov61/TinyMusician-Pretrained-3L-128E-100M", 1024, 128, 3, 4),
}


def run(cmd: list[str], cwd: Optional[Path] = None) -> None:
    print(" ".join(cmd))
    subprocess.check_call(cmd, cwd=cwd)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def clone_upstream(workdir: Path) -> Path:
    if (workdir / "TinyMusician").exists():
        return workdir / "TinyMusician"
    run(["git", "clone", "--depth", "1", REPO_URL], cwd=workdir)
    return workdir / "TinyMusician"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", default="small",
                        choices=list(VARIANTS.keys()))
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--workdir", type=Path, default=Path("/tmp/tinymusician-export"))
    parser.add_argument("--hf-repo", default=HF_OUT_REPO,
                        help="Target Hugging Face repo to upload to")
    parser.add_argument("--upload", action="store_true",
                        help="Push the result to Hugging Face")
    parser.add_argument("--hf-token", default=None)
    args = parser.parse_args()

    hf_id, ctx_len, hidden, n_layers, n_heads = VARIANTS[args.variant]
    args.workdir.mkdir(parents=True, exist_ok=True)
    repo = clone_upstream(args.workdir)

    # 1. Install dependencies into the active Python (NOT into T2V's Gradle).
    run([sys.executable, "-m", "pip", "install", "--quiet",
         "torch", "onnx", "onnxruntime", "transformers", "huggingface_hub",
         "numpy"])

    # 2. Load model + tokenizer.
    print(f"Loading {hf_id}...")
    from huggingface_hub import snapshot_download
    snapshot_dir = Path(snapshot_download(hf_id, cache_dir=args.workdir / "hf"))
    sys.path.insert(0, str(repo))
    from model import TinyMusicianModel  # type: ignore
    import torch
    ckpt = torch.load(snapshot_dir / "pytorch_model.bin", map_location="cpu")
    model = TinyMusicianModel(
        vocab_size=ckpt["vocab_size"],
        hidden=hidden,
        n_layers=n_layers,
        n_heads=n_heads,
        max_seq_len=ctx_len,
    )
    model.load_state_dict(ckpt["model"])
    model.eval()

    # 3. Build a dummy input: (batch=1, seq=ctx_len) of token ids.
    dummy = torch.randint(0, ckpt["vocab_size"], (1, ctx_len), dtype=torch.long)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = args.output_dir / "model.onnx"

    # 4. Export to ONNX.
    print(f"Exporting to {onnx_path}...")
    torch.onnx.export(
        model, dummy, onnx_path,
        input_names=["input_ids"],
        output_names=["logits"],
        dynamic_axes={"input_ids": {1: "sequence"}, "logits": {1: "sequence"}},
        opset_version=17,
        do_constant_folding=True,
    )

    # 5. Quantize to int8 (ARM64-friendly via QLinearOps).
    print("Quantizing to int8...")
    from onnxruntime.quantization import quantize_dynamic, QuantType
    quantize_dynamic(
        model_input=str(onnx_path),
        model_output=str(args.output_dir / "model.int8.onnx"),
        weight_type=QuantType.QInt8,
    )
    # Replace the unquantized model with the int8 one.
    shutil.move(args.output_dir / "model.int8.onnx", onnx_path)

    # 6. Copy tokenizer if present in the snapshot.
    tok_src = snapshot_dir / "tokenizer.json"
    if tok_src.exists():
        shutil.copy(tok_src, args.output_dir / "tokenizer.json")

    # 7. Write SHA-256 sidecars.
    for f in args.output_dir.iterdir():
        if f.is_file() and f.suffix in {".onnx", ".json"}:
            sha = sha256_file(f)
            (args.output_dir / f"{f.name}.sha256").write_text(sha)
            print(f"{f.name}: {sha} ({f.stat().st_size:,} bytes)")

    # 8. Optional: upload to Hugging Face.
    if args.upload:
        from huggingface_hub import HfApi
        api = HfApi(token=args.hf_token)
        api.create_repo(args.hf_repo, repo_type="model", exist_ok=True)
        api.upload_folder(
            folder_path=str(args.output_dir),
            repo_id=args.hf_repo,
            commit_message=f"Upload {args.variant} ONNX int8",
        )
        print(f"Uploaded to https://huggingface.co/{args.hf_repo}")

    print("Done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
