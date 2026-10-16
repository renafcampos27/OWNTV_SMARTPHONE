# libmpv 2026.10.1 — correção nativa pontual

Data: 07/10/2026. Âmbito: OwnTV Mobile e respetivo Core incorporado.

## Alteração

A dependência `tv.own.owntv:libmpv` passou de 2026.10.0 para 2026.10.1 no catálogo do Core incorporado. Nenhum ficheiro de integração Kotlin do reprodutor foi substituído. Configurações, personalizações, HLS, recuperação, buffers e volume foram mantidos. Os projetos OwnTV-main e Core externo não foram alterados.

O diff entre tags do fornecedor contém um patch funcional novo, `0002-audiotrack-no-spin-on-underrun.patch`, e documentação. Mantém mpv 3186d369f e FFmpeg n9.0.2. No backend audiotrack, uma leitura sem amostras passa a esperar até 10 ms libertando o bloqueio, em vez de repetir escritas vazias continuamente. A espera é condicional à falta de amostras; não é um atraso imposto a todas as mudanças de canal.

## Comparação efetiva dos pacotes AAR

Os dois AAR obtidos pelo Gradle foram comparados por entrada, usando SHA-256 do conteúdo descomprimido:

- Nenhuma entrada adicionada ou removida.
- Apenas alterados: jni/arm64-v8a/libmpv.so, jni/armeabi-v7a/libmpv.so e jni/x86_64/libmpv.so.
- classes.jar, libplayer.so e restantes bibliotecas, incluindo FFmpeg, têm conteúdo idêntico.

Esta comparação confirma o âmbito binário da substituição; não demonstra o comportamento do patch num dispositivo.

## Validação

BUILD SUCCESSFUL.

- Dependência resolvida no runtime release: tv.own.owntv:libmpv:2026.10.1.
- Compilação Kotlin standardRelease: aprovada.
- lintVitalStandardRelease: aprovado.
- Testes unitários de player-core: 417; falhas: 0; erros: 0; ignorados: 0.

A compilação reutilizou tarefas cujas entradas não mudaram, coerente com o classes.jar idêntico.

A correção é nativa e os testes JVM não exercitam AudioTrack. Confirmar num dispositivo físico: aberturas repetidas de diferidos com mpv, arranque com som, pausa/retoma, procura, reprodução contínua e CPU perante interrupções de áudio. Verificar também direto em ExoPlayer como controlo. Não há prova de que a correção resolva falhas HTTP, MPEG-L2 ou o zapping da app. Não foi gerado APK.

## Fontes

- Release: https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.1
- Commit: https://github.com/ahXN00/OwnTV_libmpv/commit/72277f13
- Comparação: https://github.com/ahXN00/OwnTV_libmpv/compare/v2026.10.0...v2026.10.1

## Reversão

Se os testes físicos mostrarem regressão, repor apenas `libmpv = "2026.10.0"` no catálogo do Core incorporado e gerar novamente o APK. Não substituir o Core ou o código Kotlin personalizado.
