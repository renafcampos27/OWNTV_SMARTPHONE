# Integração seletiva Core 1.0.66 / libmpv 2026.10.3

Data: 11/10/2026. Estado: três fases implementadas e verificadas por compilação, testes e lint. Validação física pendente. Não foi gerado APK.

## Âmbito e versões

Alterações limitadas à OwnTV Mobile e ao Core privado em `vendor/OwnTV_Core`. Não foram alterados OwnTV-main nem o Core externo. A integração é seletiva: não substitui o Core personalizado pelo Core upstream completo.

- libmpv: 2026.10.2 → 2026.10.3, por dependência Maven fixada.
- Media3: mantém 1.11.1.
- Mantém Android mínimo 8.0/API 26; ARMv7, ARM64 e x86_64.
- Sem migração de base de dados ou mudança do formato de backup.
- Mantém HLS estrito, exceções por canal, identidade de sintonia, passagem entre motores, limites de recuperação, reserva de buffer independente da latência e volume interno a 100%.

## Fase 1 — motor, cache e pesquisa

1. Atualizada libmpv para .3, incluindo o binário upstream com correções de MediaCodec/TLS e redução de componentes nativos. Estas correções nativas não alteram o cliente HTTP do ExoPlayer.
2. O watchdog mpv observa `paused-for-cache` e a duração efetiva da cache. Quando a cache está a crescer durante uma espera por dados, não conta esse intervalo como congelamento de reprodução. Se deixar de crescer, continua a aplicar as proteções existentes. Cada sintonia limpa as amostras antigas. O crescimento da cache não restaura o orçamento de reconexão como se fosse reprodução estável.
3. Pesquisa FTS separa palavras por pontuação, preservando letras Unicode e números. Mantém o limite local de resultados; não importa a consulta upstream de 500 resultados completos.
4. A classificação de áudio no ExoPlayer aceita faixas acima das capacidades anunciadas quando o renderer as declara reproduzíveis. Uma falha efetiva continua sujeita a diagnóstico e recuperação.

## Fase 2 — áudio FFmpeg partilhado

1. Migradas as quatro classes Java FFmpeg para `tv.own.owntv.player.ffmpeg`, com o contrato JNI da nova `libowntvffmpeg.so`.
2. Removidas as classes e os binários antigos `libffmpegJNI.so`. O FFmpeg de áudio do ExoPlayer usa as bibliotecas que a libmpv já fornece; não ficam duas extensões FFmpeg registadas.
3. Mantém a preferência opcional de áudio por software: coloca o renderer FFmpeg antes do MediaCodec quando selecionada; no modo normal mantém a prioridade dos decoders do dispositivo. Não força o vídeo para software.
4. Mantém o processador estéreo local, com máscaras, mapeamento de canais e mistura normalizada. Não importa controlo de ganho/night mode; volume interno continua unitário.
5. `ExoAudioGate` considera decoder do dispositivo, FFmpeg e passthrough permitido. MP2/MPEG-L2 é identificado corretamente. Diferidos sem faixa de áudio reproduzível emitem uma falha uma vez por início, respeitando a escolha de motor.
6. Seletores ExoPlayer permitem reavaliar faixas quando as capacidades dos renderers mudam.
7. mpv escolhe layouts PCM conforme a saída anunciada e só escreve `audio-channels` quando o valor muda. Observa ligação/desligação de saídas e remove o callback na libertação. APIs recentes têm guardas de versão; saídas desconhecidas mantêm o comportamento seguro anterior.
8. Atualizados regras de preservação JNI/R8, aviso de licenças e ferramenta de verificação. Fontes Java, artefacto Maven e bibliotecas nativas têm hashes fixados.

## Fase 3 — rede, comandos e segundo plano

1. mpv define `network-timeout` em cada carga: 10 segundos no direto; 60 segundos nos diferidos/ficheiros. É um timeout de operação de rede, não o pré-buffer nem o tempo da mudança automática de versão. Os limites existentes de tentativas mantêm-se.
2. MediaSession trata Play/Pause quando o comando envia apenas `ACTION_UP`. Pares normais DOWN/UP ficam entregues ao Android, evitando executar duas vezes. A identificação usa tecla e instante da pressão; mudar de motor limpa as teclas pendentes. Pausa manual cancela uma retoma devida a interrupção anterior.
3. A definição existente de reprodução em segundo plano entra no snapshot inicial. Quando ativa, os ExoPlayers aplicam `WAKE_MODE_NETWORK`; quando desativada, `WAKE_MODE_NONE`. Media3 controla os locks conforme o estado e a intenção de reprodução, incluindo BUFFERING. Pausa, paragem/libertação e desativação da opção libertam os recursos correspondentes. A permissão WAKE_LOCK é declarada na biblioteca.

## Alterações upstream não copiadas indiscriminadamente

- Access units H.264: continuam opcionais por canal, sem custo adicional imposto a todos os canais.
- Buffer upstream ligado à latência: não substitui a separação local entre reserva de dados e atraso face ao direto.
- Reconexão DASH e tratamento 458 de TS bruto: propostas condicionais da análise, não recomendadas globalmente para HLS. Não foi introduzida reconexão infinita nem uma troca de formato automática nova.
- Filmes, night mode e novos controlos de volume: não reintroduzidos.
- Partilha do Core com a app TV: não realizada nesta integração.

## Verificação concluída

- Compilação Kotlin Standard Release da app: passou.
- Testes Mobile: 71; Core: 1.114; player: 444. Total: **1.629**, zero falhas, erros ou testes ignorados.
- `lintVitalStandardRelease`: passou, incluindo análise das dependências Core/player.
- Inventário de literais: passou; novos parâmetros de rede são classificados como técnicos.
- Verificação da ponte partilhada: hashes, três ABIs, identificadores JNI e bibliotecas necessárias. Alinhamento ELF de 16 KiB nas ABIs de 64 bits; ARMv7 tem alinhamento mínimo de 4 KiB. Isto não equivale a executar JNI num dispositivo.
- União das bibliotecas Standard Release: passou. Confirmadas libowntvffmpeg, libavcodec, libavutil, libswresample e libmpv em ARMv7/ARM64; ausência de libffmpegJNI.
- `git diff --check`: passou.

Tamanho das bibliotecas nativas dentro dos AARs, antes de compressão e sem a extensão antiga separada: ARM64 24,78 → 21,20 MiB; ARMv7 22,42 → 19,52 MiB. Não é uma medição do tamanho final do APK, RAM ou CPU da aplicação.

## Testes físicos antes de considerar a integração validada em produção

1. Abrir AAC e MP2/MPEG-L2 com áudio automático e software; verificar que som e diagnóstico coincidem com o caminho efetivo.
2. Alternar A/B/C/A repetidamente, voltar à lista e reabrir o canal; testar diferidos e deslocação na barra temporal.
3. Pré-buffer com origem lenta: cache crescente não deve disparar recargas; interrupção sem progresso deve continuar limitada e observável.
4. HDMI/Bluetooth/auscultadores: ligar e desligar saída, confirmar estéreo/multicanal e ausência de recargas repetidas.
5. Home/regresso, ecrã desligado e pausa manual com segundo plano ligado/desligado; confirmar comportamento e consumo.
6. Comando com DOWN/UP e, quando disponível, comando que só envia UP: uma ação por pressão.
7. Origens HLS lentas em mpv: verificar o novo timeout de 10 s. Não foi demonstrado que todas as origens o toleram sem recuperação adicional.

Não há medição de CPU/RAM nem teste físico de som, zapping ou JNI nesta sessão. Compilação e testes automáticos não garantem ausência de falhas específicas do fornecedor ou do firmware.

## Rastreabilidade

- Análise inicial: `ANALISE_CORE_1_0_66_LIBMPV_2026_10_3_2026_10_11.md`.
- Fontes: [Core 1.0.66](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.66) e [libmpv 2026.10.3](https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.3).
- Auditoria atual do áudio: `vendor/OwnTV_Core/tools/ffmpeg/audio_manifest.json` e `verify_shared_audio.py`; `verify_audio.py` mantém o ponto de entrada anterior.
- Inventário `tools/mpv/native_manifest.json` assinalado como histórico, não como inventário da .3.
- Cópia anterior à integração: `C:/Users/renat/AppData/Local/Temp/owntv-before-core66-20261011`.
