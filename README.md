<div align="center">

# Litegram

**O mensageiro leve com alma de Telegram e um toque a mais: música do YouTube em um toque.**

Só conversa de verdade. Sem chamadas, sem stories, sem bots — e com um botão
♪ que baixa qualquer música do YouTube e envia como áudio no chat.

[![Build APK](../../actions/workflows/build-apk.yml/badge.svg)](../../actions/workflows/build-apk.yml)

[Como compilar](#-como-compilar) · [O botão de música](#-baixar-música) · [O que saiu](#-o-que-saiu) · [Aviso legal](#-aviso-legal)

</div>

---

## Por que existe o Litegram?

Celular simples, internet fraca, pouca memória — e mesmo assim dá pra conversar
sem abrir mão de nada essencial. O Litegram pega o app de mensagens mais rápido
que existe, tira tudo que pesa e deixa só o que importa: **conversar e
compartilhar**. De brinde, ganha uma função que o original não tem.

## O que tem aqui

- **Chat completo** — texto, fotos, galeria, documentos e música/áudio
- **♪ Baixar Música** — pesquise o nome ou cole o link do YouTube / YouTube Music,
  escolha na lista e a música chega no chat como arquivo de áudio (m4a ~128 kbps,
  com título e artista, sem capa)
- **Só gente de verdade** — perfis de robô não aparecem na busca nem nos contatos

## O que saiu

| Removido | Por quê |
|---|---|
| Chamadas de voz e vídeo (inclui o motor nativo) | é o que mais pesa no APK |
| Stories | menos consumo de RAM e dados |
| Robôs, teclados de robô e respostas inline | foco total em conversa humana |
| Arquiteturas e idiomas extras (fica só ARM 64 bits + PT-BR/EN) | APK bem menor |

Resultado: APK na casa dos **30–60 MB** (versão otimizada) contra ~100 MB do app completo.

## Como compilar

### Opção 1 — pela nuvem (mais fácil, não esquenta o celular)

1. Abra a aba **Actions** aqui do repositório
2. Entre em **Litegram APK** e clique em **Run workflow**
3. Em ~15 minutos, baixe o arquivo `litegram-apk` em **Artifacts**

### Opção 2 — no Termux (celular potente, com paciência)

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

Detalhes técnicos das mudanças estão em [LITEGRAM.md](LITEGRAM.md).
Para compilar localmente do zero você vai precisar do NDK 27, SDK 36 e das suas
próprias chaves de API — o passo a passo original está na
[documentação do projeto-base](https://core.telegram.org/api/obtaining_api_id).

## Aviso legal

- O Litegram é um projeto **não oficial**, feito para estudo e uso pessoal.
  Ele usa o código aberto do aplicativo Telegram para Android como ponto de
  partida — todos os créditos do protocolo, da API e do código original vão
  para a equipe do Telegram.
- O nome e o logo do Telegram pertencem aos seus donos; este app usa
  identidade visual própria.
- Antes de distribuir qualquer APK gerado daqui, coloque **suas próprias**
  chaves (`api_id`, `google-services.json`, keystore de assinatura).
  Os arquivos de exemplo do repositório são fictícios e servem só para compilar.
- Uso da API segue as regras da plataforma do Telegram: tenha sua própria
  chave, cuide dos dados dos usuários e publique seu código (licença GPL).

---

Feito com carinho para quem tem pouco espaço no celular e muito amor por música. ♪
