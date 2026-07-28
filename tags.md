# T2V expressive speech tags

T2V tags are written as `{{command value}}`. A persistent tag affects all
following text until it is changed or reset. A reaction tag is emitted once at
its exact position.

## Emotion

```text
{{emotion neutral}}
{{emotion happy}}
{{emotion sad}}
{{emotion angry}}
{{emotion afraid}}
{{emotion surprised}}
{{emotion disgusted}}
{{emotion excited}}
{{emotion calm}}
{{emotion anxious}}
{{emotion confident}}
{{emotion confused}}
{{emotion disappointed}}
{{emotion hopeful}}
{{emotion serious}}
{{emotion sarcastic}}
{{emotion sympathetic}}
{{emotion tired}}
```

Custom values are allowed: `{{emotion "quietly joyful"}}`. Models supporting
natural-language direction receive the original value. Other engines use the
closest safe preset.

## Delivery

```text
{{delivery normal}}
{{delivery whisper}}
{{delivery shout}}
{{delivery soft}}
{{delivery loud}}
{{delivery slow}}
{{delivery fast}}
{{delivery dramatic}}
{{delivery narrator}}
{{delivery conversational}}
{{delivery news}}
{{delivery secretive}}
{{delivery hesitant}}
{{delivery urgent}}
```

Aliases:

```text
{{whisper}}  = {{delivery whisper}}
{{shout}}    = {{delivery shout}}
```

## One-shot vocal reactions

These occur once before the following words:

```text
{{breath}}
{{breath in}}
{{breath out}}
{{sigh}}
{{laugh}}
{{chuckle}}
{{giggle}}
{{cry}}
{{sob}}
{{gasp}}
{{yawn}}
{{cough}}
{{clear_throat}}
{{sniff}}
{{pant}}
{{hmm}}
```

Reaction support is capability-dependent. Eleven v3 and compatible
instruction-based models can synthesize reactions. Kokoro and Piper must not
pronounce tag names; unsupported reactions are ignored with the surrounding
speech preserved.

## Prosody and language

```text
{{voice "voice-id"}}
{{lang ru-RU}}
{{speed 0.90}}
{{volume 80%}}
{{pitch 0.95}}
{{pause 700ms}}
{{pause 0.7s}}
{{pause.short}}
{{pause.long}}
{{pause random 500 1200}}
```

Safe ranges applied by T2V:

- speed: `0.5..2.0` locally; provider limits may be wider;
- volume: `0..4`, normally `0..1`;
- pitch: `0.5..2.0`;
- pause: non-negative milliseconds.

## Emphasis and pronunciation

```text
{{emphasis reduced}}
{{emphasis moderate}}
{{emphasis strong}}
{{pronounce "слово" ipa="/.../"}}
```

`pronounce` is reserved for the pronunciation-dictionary stage. Until a
provider-specific phoneme implementation is active, it remains visible in the
project but is not sent as spoken text.

## Reset

```text
{{reset emotion}}
{{reset delivery}}
{{reset prosody}}
{{reset voice}}
{{reset all}}
```

## Project and audio-track tags

These are not vocal reactions:

```text
{{chapter "Chapter title"}}
{{music "calm cinematic background" -12}}
{{sfx "wooden door closes" 0}}
{{cmd key=value}}
```

Music and SFX are placed on their own editor tracks. They must never be passed
to a TTS model as words to pronounce.

## Engine mapping

| Engine | Emotion/delivery | Reactions | Mapping |
|---|---:|---:|---|
| ElevenLabs `eleven_v3` | Native | Native | Square-bracket audio tags |
| Gemini TTS | Native | Prompt-dependent | Natural-language direction |
| OpenAI `gpt-4o-mini-tts` | Native | Prompt-dependent | `instructions` |
| Azure compatible neural voice | Voice-dependent | Voice-dependent | SSML `mstts:express-as` |
| Kokoro | Approximation | No | Speed, pitch, volume and punctuation |
| Piper/VITS | Approximation | No | Speed and volume; unsupported cues omitted |

T2V never promises a reaction when the selected model lacks that capability.
The editor should show the effective support level before generation.

## XML-style audio tags (2026-07-28)

In addition to `{{music "name" -2dB}}` and `{{sfx "name" 200ms}}` (which
are recognized but do not yet trigger generation), T2V supports
XML-style tags that explicitly insert audio clips into the timeline:

```text
<music>тёплый эмбиент-пэд, 80 BPM, 10 секунд</music>
<sfx>старая деревянная дверь, скрип петель, 1.5 секунды</sfx>
```

These are parsed by `LTVMarkupParser.extractAudioTags()` and
`parseSpans()` (which breaks voice chunks at each tag). The `AudioTagInserter`
generates a WAV via `GeneratorRegistry` and persists `AudioClipEntity`
on the right track. See `docs/LTV_MARKUP.md` for the full specification.

The legacy `{{music "..."}}` / `{{sfx "..."}}` syntax is kept for
backward compatibility but currently does not trigger generation.
