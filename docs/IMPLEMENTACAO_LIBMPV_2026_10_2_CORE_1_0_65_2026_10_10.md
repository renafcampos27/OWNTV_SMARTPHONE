# OwnTV Mobile — integração libmpv 2026.10.2 e Core 1.0.65

Data: 10/10/2026. Âmbito: projeto Mobile e respetivo `vendor/OwnTV_Core`. Autorização: implementar todas as recomendações da análise e do council de 09/10/2026.

## Resultado

Integração seletiva; não foi substituído o Core completo. O projeto TV `OwnTV-main` e o Core externo não foram alterados. A base de dados mantém a versão 51 e o backup a versão 25. Não foi gerado APK.

| Área | Implementação e comportamento |
|---|---|
| Biblioteca nativa | Dependência libmpv 2026.10.2 completa, com as correções upstream MediaCodec/FFmpeg. As adaptações Kotlin permanecem locais. Não altera diretamente o descodificador ExoPlayer. |
| Codecs e hardware | Normalização dos nomes AVC/HEVC e das implementações Google/On2; decisões de probe/hardware de diferidos adaptadas sem impor software globalmente. |
| Diferidos | Preservação de `isArchive` e posição nos retries/reloads e restauração defensiva. Reassociação da superfície no dispatcher Main com identidade e geração verificadas. |
| Arquivo sem imagem | Corrige a classificação da falha; recuperação finita. Outro motor só é tentado quando a política autoriza; motor exclusivo continua exclusivo. |
| Primeiro fotograma congelado | Guarda monotónica que exige progresso real, respeita pausa, buffering e modo áudio. Uma tentativa de reset; fallback permitido ou erro, sem ciclo infinito. |
| Diferido e continuação | Cancelamento e verificações de revisão, geração, perfil, canal e programa para impedir que uma resposta antiga substitua o pedido atual. Arquivo parado só regressa ao direto se o programa ainda estiver no ar; arquivos históricos apresentam erro. |
| Rede | DNS com quatro workers globais, sem fila ilimitada, cache positiva limitada a 128 entradas/5 minutos e identidade da configuração DNS. Prazo HTTP por salto, após primeiro fotograma, callbacks associados à sintonia, dentro dos budgets existentes. Pedidos LL-HLS de blocking reload (`_HLS_msn`) mantêm o timeout normal de leitura. |
| Áudio | Preview silenciado não é classificado como falha. Saída PCM mono/estéreo pode receber uma reconstrução limitada do sink; não fixa permanentemente estéreo nem redefine globalmente passthrough. Volume interno permanece 100%. |
| Gravação HLS | AES-128 para segmentos TS, IV explícito ou sequência, rotação de chave mesmo com URI repetida, desencriptação por blocos e integridade antes de commit. Chaves limitadas a 16 bytes e não persistidas. |
| Atualização automática | Opções existentes de listas/EPG passam a ser executadas no arranque/regresso à app; WorkManager KEEP evita substituição do trabalho já existente. Trabalhos automáticos aguardam reprodução/recursos/gravação livres antes de começar; verificações interrompidas podem voltar a ser tentadas. |
| Restauro | Catálogos vazios após importação são enfileirados para atualização. O assistente inicial aguarda o fim da importação antes de apresentar o shell. Restaurar não significa que o download das listas terminou; o progresso normal continua aplicável. |
| Gravação manual | Em Gravações, “Agendar por data e hora”: pesquisar canal, selecionar data/início/fim e agendar sem EPG. Máximo de 24 horas reais; intervalo que termina à mesma hora ou antes passa para o dia seguinte. |
| Pesquisa de programas | Nova secção “Programas de TV” na pesquisa. Considera programas em curso/próximas 12 horas, fontes do perfil, canais/categorias visíveis e correções horárias. Reutiliza o painel de programa do Guia. |
| Stalker | Primeira consulta preserva o comportamento habitual; rejeição de identidade permite uma única segunda consulta com modelo MAG. Erro final genérico, sem mostrar o texto bruto do portal. |

## Limites deliberados

- HLS AES-128 não é DRM/SAMPLE-AES. Segmentos encriptados com byte ranges ou init fMP4 continuam a ser recusados explicitamente; o suporte existente aos formatos sem encriptação permanece.
- Segmento de gravação limitado a 64 MiB, buffer de transferência de 128 KiB; uma chave é obtida novamente por segmento/tentativa para aceitar rotação no mesmo URI.
- Gravações manuais não ficam ligadas a uma identidade EPG; a reconciliação do Guia não desloca o intervalo escolhido. Usam as verificações existentes de armazenamento, conflitos e limites do fornecedor.
- O fuso é o do dispositivo. Horas inexistentes na mudança de hora são recusadas; horas repetidas usam a primeira ocorrência. A UI explica este comportamento.
- Pesquisa: até 40 resultados de programas, páginas de 200, até 2 000 candidatos; não é pesquisa de todo o arquivo histórico. São consultas leves, canceláveis, sem carregar descrições longas.
- A atualização automática aguarda até dois minutos antes de devolver retry ao WorkManager se houver atividade. Esta proteção verifica o estado antes do início; não suspende um sync que já estava a executar quando se abre um canal.
- As correções de rede não anulam HTTP 403, políticas do fornecedor ou limites de sessões.
- O caminho de BackgroundRestore foi protegido, mas não é atualmente chamado pela Mobile; não se atribui benefício visível a esse caminho sem utilização.

## Validação

Resultado final: **BUILD SUCCESSFUL**, compilação Kotlin Standard Release, testes Standard Debug da app, testes Debug do Core/player-core e `lintVitalStandardRelease` concluídos.

| Módulo | Testes | Falhas/erros |
|---|---:|---:|
| Mobile | 67 | 0 |
| Core privado | 1102 | 0 |
| Reprodução | 429 | 0 |
| Total | 1598 | 0 |

A verificação de internacionalização passou com classificações explícitas para SQL/protocolos/logs, sem desativação do controlo. O Lint teve uma falha interna numa execução intermédia; a validação completa repetida sem paralelismo e a execução final passaram, sem supressão do analisador.

`dependencyInsight` confirmou `tv.own.owntv:libmpv:2026.10.2` em Standard Release Runtime. Comparação AAR Maven/release: payloads mpv/FFmpeg e classes iguais; a ponte JNI difere apenas na secção ELF `.note.gnu.build-id` nas três ABIs. O Core continua a ser o projeto privado personalizado, não uma substituição binária por Core 1.0.65.

O Gradle ainda informa de APIs descontinuadas para uma futura versão 10; esse aviso não impediu a validação. Não se afirma ausência de todos os avisos do projeto.

Verificação SQLite adicional: isolamento de fontes, correspondências EPG manuais, pesquisa literal de %/_, paginação e intervalo temporal passaram.

A validação automática não substitui os ensaios físicos Android. Prioridades: zapping A/B/C repetido; voltar de fullscreen; diferido com/sem vídeo e pausa; áudio AAC/MPEG-L2; gravação AES-128 com rotação; backup/restauro em instalação limpa; agendamento noturno/DST; pesquisa com categorias ocultas; atualização automática enquanto existe reprodução/gravação.

## Partilha futura com a TV

Faz sentido manter uma base comum de Core e libmpv quando as adaptações das duas aplicações estiverem comparadas e testadas. Primeiro alinhar versões de dependências e APIs, depois extrair lógica comum de reprodução/rede/gravação, mantendo UI, ciclo de vida, D-pad/toque e definições próprias em cada app. Distribuir uma versão fixa do Core comum com testes das duas aplicações; evitar uma pasta externa mutável que atualize ambas involuntariamente. Esta integração não iniciou essa migração.

## Referências

- [Análise e council](ANALISE_LIBMPV_2026_10_2_CORE_1_0_65_COUNCIL_2026_10_09.md).
- [Pendências do OwnCore](ATUALIZACOES_OWNCORE_PENDENTES_DE_IMPLEMENTACAO.md).
- [Release libmpv 2026.10.2](https://github.com/ahXN00/OwnTV_libmpv/releases/tag/v2026.10.2).
- [Release Core 1.0.65](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.65).

- [Apple: blocking reload LL-HLS](https://developer.apple.com/documentation/http-live-streaming/enabling-low-latency-http-live-streaming-hls): o servidor pode aguardar por uma versão futura da playlist; esse pedido não deve ser tratado como falta anómala de resposta pelo novo prazo curto.
