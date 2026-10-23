# OwnTV Mobile 1.1.0 — análise para a versão personalizada

Data: 18/10/2026. Estado: análise; nenhuma implementação, build, APK ou teste de desempenho.

## Fontes e versão efetivamente comparada

- [Mobile v1.1.0](https://github.com/ahXN00/OwnTV_Mobile/releases/tag/v1.1.0), publicada em 17/10/2026. Fonte fixa no commit 52c35d0b108fc2ae195e682612bf1c548e662bc3.
- [Core 1.0.68](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.68), usado pelo catálogo dessa Mobile. Fonte fixa no commit c14e944b8d17e5cbb559d116afb30dc3b34864cb.
- Projeto personalizado: [Mobile](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main), HEAD observado 8e10ccc, sem alterações locais antes desta análise.
- Consultado o [registo de pendências Core](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/docs/ATUALIZACOES_OWNCORE_PENDENTES_DE_IMPLEMENTACAO.md) e o [lote 1/2 concluído](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/docs/IMPLEMENTACAO_ADAPTACAO_LOTES_1_2_2026_10_16.md).

Cópias de comparação descarregadas apenas para diretórios temporários. TV, Core externo e código Mobile não foram alterados. A análise combina notas oficiais e comparação dirigida do código; não é uma auditoria de todas as linhas dos dois projetos.

## Decisão principal

Recomendada integração seletiva: primeiro CPU/memória/interface, depois diferidos e correções concretas de reprodução. Não substituir o projeto/Core nem importar a navegação completa.

A etiqueta Maven 1.0.62 do catálogo local não descreve o conjunto de correções atual: settings.gradle usa o Core privado via includeBuild. Esse Core já tem adaptações posteriores e a base de dados está em **v51**, enquanto o upstream 1.0.68 usa **v49**. Mesmos números de migração podem representar alterações diferentes; adicionar campos novos exige uma migração própria testada. Ambos usam Media3 1.11.1 e libmpv 2026.10.3 nos catálogos inspecionados: esta publicação não exige, por si só, trocar o binário mpv.

## 1. Prioridade alta — trabalho e memória da interface

| Adaptação | Diferença local verificada | Como integrar |
|---|---|---|
| Paging limitado | LiveViewModel e GuideViewModel locais não definem maxSize; upstream limita Live a 400 e Guia a 300 itens carregados. | Adaptar orçamento e carregamento inicial, medindo listas grandes. Preservar âncora, ordem personalizada e regresso ao canal atual; não copiar valores sem ensaio. |
| Menos atualizações de ecrã por segundo | Upstream passa relógio/offset como State e lê nos componentes pequenos que desenham esses valores. | Adaptar HUD/timeline e cabeçalhos; confirmar quais leitores locais ainda recompõem o nó superior. Não mover leituras funcionais do player para subscrições dependentes da UI. |
| Parar ciclos sem reprodução | VodTuner local acorda a cada 5 s durante a vida do serviço, mesmo sem conteúdo. Upstream cria o saver apenas com item ativo. | Parar o job quando não é necessário; conservar a última gravação de posição ao parar/trocar e as revisões Mobile de lifecycle. O timer é de progresso, não um mecanismo de favoritos. |
| Listas auxiliares mais económicas | PR64 separa cabeçalhos de velocidade/armazenamento, memoriza filtros e usa contentType/chaves; reduz consultas por linha. | Portar só consumidores presentes na nossa app; gravações já têm monitor ligado à visibilidade e não devem perder essa proteção. |
| EPG agora/seguinte com limite | Upstream limita os mapas agora/seguinte a 500 entradas, além das páginas. | Usar cache limitada com invalidação/expiração e releitura quando se regressa. Não confundir o limite de cache com cortar o catálogo ou dias do EPG. |

Evidência local: [Live Paging](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/live/LiveViewModel.kt:266), [Guide Paging](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/guide/GuideViewModel.kt:353), [timer VodTuner](C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main/app/src/main/java/tv/own/owntv/mobile/ui/screens/library/VodTuner.kt:199).

Fonte: [PR64 — otimizações Mobile](https://github.com/ahXN00/OwnTV_Mobile/pull/64).

## 2. Prioridade alta — diferidos e robustez, através de deltas do Core

| Candidato | Benefício | Condições |
|---|---|---|
| Fuso automático Xtream pela lista | Automatic pode usar a zona que o servidor anuncia. Os campos serverTimeZone/catchupCorrectionMin upstream não existem nos modelos locais inspecionados. | Migração própria e testes DST/UTC; não somar outra hora às correções existentes nem alterar o horário apresentado no EPG. |
| Templates de diferidos M3U | Mais placeholders e formatos, incluindo timestamp do pedido atual e formatação de datas. | Preservar templates locais autorizados, correção de request separada da apresentação e retenção real do fornecedor. Testes de URL antes de mudar o sync/parser. |
| Retenção de contexto ao repetir | Opções do arquivo, idioma, posição e motor devem sobreviver a reload/renovação. | Sobrepõe-se ao plano local de lotes 4/5. Portar apenas o delta em falta, conservando os tickets/revisões do Lote A. |
| Reconexão do direto recuado | Nova resolução de uma sessão/playlist expirada, em vez de pedir indefinidamente o manifesto antigo após quebra de rede. | Testar loopback, segmentos assinados, troca de canal e cancelamento. Não criar outro watchdog nem ciclo infinito. |
| Retry após desistência e fallback finito | Reavaliar estados terminais e temporizadores corrigidos no upstream. | Motor exclusivo continua exclusivo. Não copiar LiveTuneController inteiro, pois a versão local tem cancelamento/identidade e HLS estrito próprios. |
| Correção de áudio no motor ativo | Alterar Surround no Exo não deve iniciar uma cópia mpv. | Continua candidato já identificado no plano anterior; manter ganho interno unitário e faixas por identidade. |

As melhorias anunciam percursos corrigidos, não provam que um bloqueio específico do nosso fornecedor seja resolvido. [Notas Core 1.0.68](https://github.com/ahXN00/OwnTV_Core/releases/tag/core-1.0.68).

### Zapping de uma ligação: não importar como receita universal

A [PR32](https://github.com/ahXN00/OwnTV_Core/pull/32) melhora a medição do primeiro frame e espera a libertação mpv antes de abrir Exo num painel com uma sessão. É relevante para cruzamento de motores, mas a nossa Mobile já tem confirmação local/cancelamento HTTP. Comparar a fronteira exata e testar antes de acrescentar esperas ou substituir o handover. O ensaio publicado foi maioritariamente TS/Exo numa TV; não demonstra a mesma melhoria em HLS num smartphone nem a libertação da sessão no servidor.

## 3. Memória Android 14+ — útil, com política Mobile

O Core novo acrescenta consulta de memória a cada 5 s apenas enquanto algum motor reproduz, porque os níveis RUNNING_* deixaram de ser entregues desde API34. O código local continua baseado nos callbacks anteriores.

Faz sentido avaliar este reforço para reduzir cache em pressão real, sem reduzir continuamente a reserva de canais saudáveis. A consulta também tem custo: subscrever só reprodução relevante, parar ao terminar e atuar uma vez por episódio de pressão. Não usar UI_HIDDEN como sinónimo de falta de memória.

Fontes: [PlaybackEngines upstream](https://github.com/ahXN00/OwnTV_Core/blob/core-1.0.68/player-core/src/main/java/tv/own/owntv/player/PlaybackEngines.kt), [API Android](https://developer.android.com/reference/android/content/ComponentCallbacks2).

## 4. Prioridade média — imagem, pesquisa e comandos

- **Imagens:** há singleton Coil com cliente derivado, retry/HTTP2 para imagens e cache explícita. A Mobile local não tem esta configuração. Reutilizar a ideia com orçamento adequado ao aparelho; não transportar os 25% de memória/256 MiB de disco como requisito universal. Não mudar o cliente HLS para HTTP2 por causa dos logótipos. Evitar crossfade, pré-carga de posters e outros efeitos que o utilizador não quer.
- **Canal anterior/seguinte na categoria:** a barra upstream tem callbacks explícitos para canais, separados dos botões VOD. Adaptar ao conjunto visível, ordem personalizada e grupos; não confundir com histórico/recall. A barra local ainda só contém os controles skip VOD nesse ponto.
- **Pesquisa:** a nossa app já pesquisa programas EPG. Aproveitar filtros TV/Programas e resultados progressivos; não voltar a apresentar “pesquisa de programas” como novidade. Avaliar o limite local de 40 resultados por tipo sem trocar por uma consulta ilimitada.
- **Compatibilidade de formulários:** comparar teclado password/numeric PIN, seletores longos e chave TMDB persistida onde as rotas ainda existam. Só portar falhas reproduzidas; os PINs locais já foram revistos no lote 1.
- **Importação get.php reconhecida como Xtream:** candidata ao lote 6 anterior. Confirmar API, cancelar corretamente e preservar UA/Referer/EPG/HLS; não converter listas existentes silenciosamente.

## 5. O que já existe ou precisa de inventário, não de nova implementação

- Gravação manual por dia/horário: já há ManualRecordingViewModel/sheet.
- Estado de ficheiro indisponível: implementado no lote 2, com mais estados que “File missing”.
- Pesquisa por título EPG: já está no SearchReader e SearchScreen.
- Remover um canal de categoria personalizada: já há removeFromCustomCategory no LiveViewModel.
- Volume local controlado pelo sistema: já há SystemVolumeSwipe. O upstream acrescenta boost interno até 150% no mesmo percurso; **não copiar**, o requisito local é 100%.
- Auto-refresh com prioridade à reprodução/gravação: já há AutoRefreshGate em CatalogSyncWorker/EpgSyncWorker. Comparar apenas diferenças concretas.
- Backup no arranque: preservar os tickets SAF, conclusão após destino final e identidades do lote 1/2; inventariar somente a barreira do setup para não abrir o shell antes do restauro terminar.
- Diferido sem imagem: a pendência antiga já foi integrada seletivamente; reavaliar só novos deltas do ramo. Não a reabrir como funcionalidade inteiramente ausente.

## 6. Funcionalidades opcionais e exclusões

- **Sports Hub:** resultados, jogos, associação a canais e alertas são funcionalidade nova, com dependência de serviços e trabalho próprio. Só se houver interesse; módulo desativável, sem jobs/polling quando desligado. Não importar a navegação “Library” que reintroduz filmes/séries.
- **Retomar cópia local do canal — perguntar/sempre/nunca:** útil se o timeshift local fizer parte do uso. Exige confirmar que a sessão/ficheiros continuam válidos e respeitar o canal de arranque; não reintroduzir comportamento de último canal que o utilizador rejeitou.
- **ClearKey inline legítimo:** compatibilidade opcional quando a lista fornecida contém autorização/chave. Não oferece acesso a conteúdo sem direitos e não corrige instabilidade HLS genérica. Precisa de testes do percurso DRM que ainda falta.
- **Stage/fonte/ícone:** apenas opção visual posterior, sem assumir ganho de CPU. Não importar animações, vidro complexo, botões de áudio removidos ou novos separadores sem utilidade.
- **Software acima de 1080p:** não ativar globalmente. O guard local existe para evitar sobrecarga; eventual exceção manual por canal precisa de hardware/benchmark.
- **Downloads paralelos:** não usar como otimização da reprodução HLS ou do zapping. Downloads finitos com Range validado são outro problema; não aumentar sessões de fornecedor com limite de uma.
- Favoritos/histórico de VOD, filmes/séries, OpenSubtitles UI e boost interno permanecem excluídos.

## Ordem proposta, sem execução

1. **Completar o Lote 3 existente**, incorporando Paging limitado, cache agora/seguinte, estado visível e recomposição restrita. A nossa emissão debounce().drop(1) do Guia continua a exigir a correção já planeada; a release não substitui esse diagnóstico.
2. **Lotes 4/5:** aplicar os deltas úteis de player/diferidos do Core68 com migração compatível e testes de owner/motor/pausa.
3. **Comandos e pesquisa:** canal anterior/seguinte, filtros e formulários; cache de imagens sem efeitos.
4. Sports, Stage, retoma local e outras opções só depois de decidir a sua utilidade.

Sem promessa de aceleração, ausência de bloqueios ou melhoria de qualidade audiovisual: não foram feitos benchmarks nem ensaios em dispositivos. Não substituir as garantias locais dos lotes 1/2 por uma cópia do upstream.
