# Litegram

Telegram ultra-lite para Android — só o essencial, com um extra: **Baixar Música do YouTube**.

> Baseado no [Telegram oficial](https://github.com/DrKLO/Telegram) (v12.10.6). Branch `litegram`.

## O que foi removido

| Corte | Economia |
|---|---|
| Chamadas VoIP / tgcalls (nativo) | ~8–15 MB na `.so` |
| Stories (lista e visualização) | menos RAM e código morto |
| Bots: busca, teclado, inline, attach-menu | app mostra só gente de verdade |
| 1 ABI (`arm64-v8a`) + idiomas EN/PT-BR | ~20–40 MB vs build universal |

APK esperado: **~30–60 MB** (release) em vez de ~80–120 MB.

## O que foi adicionado

### Baixar Música (YouTube / YouTube Music)
Botão **♪ Baixar Música** logo após **Documento** no menu de anexo:
- Barrinha de pesquisa (nome da música ou link: `watch?v=`, `youtu.be`, `/shorts/`, `/embed/`, `music.youtube.com`)
- Lista de resultados com título, canal e duração — toque para enviar
- Baixa a stream de áudio **m4a ~128 kbps** direto da `googlevideo` via API pública Piped (4 instâncias com fallback)
- Envia como **mensagem de áudio normal** (sua conta, sem bot), com metadata título/autor e sem capa

## Compilar

### Nuvem (recomendado) — GitHub Actions
1. Abra **Actions → Litegram APK → Run workflow**
2. Baixe o artefato `litegram-apk`

### Local (Termux, Poco X7 Pro ou similar)
```bash
nice -n 19 ./gradlew :TMessagesProj_App:assembleAfatDebug \
  --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" \
  -Dorg.gradle.daemon=false \
  -Dorg.gradle.parallel=false \
  -Dorg.gradle.caching=true \
  --console=plain
cp TMessagesProj_App/build/outputs/apk/afat/debug/app.apk /sdcard/Download/Litegram.apk
```

> No Termux, o `clang` do NDK sob qemu não compila arquivos `.S` em 2 estágios:
> o `breakpad_getcontext_arm64.o` pré-compilado ao lado do `CMakeLists.txt` é usado
> automaticamente nesse caso.

## Estrutura das mudanças (`git diff master...litegram`)
- `TMessagesProj/.../messenger/Litegram.java` — flags ultra-lite
- `TMessagesProj/.../messenger/YtMusicDownloader.java` — pesquisa + download + envio
- `TMessagesProj/.../Components/ChatAttachAlert.java` — botão após Documento
- `VoIPHelper`, `MediaDataController`, `ChatActivity`, `DialogsSearchAdapter`, `StoriesController` — bloqueios
- `TMessagesProj/jni/CMakeLists.txt` + `TMessagesProj_App/build.gradle` — `-DLITEGRAM_LITE=ON`, 1 ABI
- `.github/workflows/build-apk.yml` — build na nuvem
