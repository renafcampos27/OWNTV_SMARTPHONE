# Mobile — auditoria crítica e próximos lotes

Data: 12/10/2026. Análise estática com três agentes especializados (streaming/áudio/lifecycle; CPU/dados; interface/usabilidade) e revisão cruzada pelo conselho. Nenhuma alteração de código, build, APK ou teste físico nesta análise. Este relatório é o único artefacto criado.

Atualização de 15/10/2026: lote A implementado posteriormente, com compilação, testes e lint. Ver [relatório do lote A](LOTE_A_ESTADO_REPRODUCAO_2026_10_15.md). Os restantes lotes mantêm-se propostos; testes físicos do lote A continuam pendentes.

Continuação de 15/10/2026: lote B também implementado, com 1.651 testes, compilação e lint aprovados. Ver [relatório do lote B](LOTE_B_BACKUP_EXPORTACAO_2026_10_15.md). Lotes C/D e validação física continuam pendentes.

Âmbito: OwnTV_Mobile-main/OwnTV_Mobile-main e vendor/OwnTV_Core privado, após a integração seletiva Core 1.0.66/libmpv 2026.10.3. TV e Core externo protegidos. Preservar HLS estrito, opções por canal, motores exclusivos, limites de reconexão, reserva independente da latência e volume interno de 100%.

As linhas são referências ao checkout observado nesta data. Defeito lógico significa que existe um cenário que o código trata incorretamente; não significa que o cenário tenha sido reproduzido num dispositivo nesta sessão. Hipóteses de eventos tardios, layout e velocidade de origem são assinaladas separadamente.

## Decisão do conselho

Corrigir primeiro lifecycle/identidade de reprodução e integridade das operações de ficheiros; depois atualização/estados do EPG e trabalho invisível; só depois melhorias instrumentadas. Nenhum achado justifica mais paralelismo indiscriminado, buffers maiores, atraso em todos os canais ou substituição global dos motores.

## Lote A — estado e propriedade de reprodução

### A1 — Home durante BUFFERING pode contrariar segundo plano desligado — P1

Evidência: app/src/main/java/tv/own/owntv/mobile/MainActivity.kt:280–282,305–311.

onStop só pausa quando isPlaying=true. Durante BUFFERING a reprodução pode continuar solicitada, mas isPlaying=false; o carregamento termina e o som pode começar depois de sair da app. onStart usa toggle quando !isPlaying, podendo pausar uma retoma já solicitada pela notificação mas ainda em BUFFERING. Os booleanos de reposição de vídeo/pausa não identificam motor, item ou revisão.

Solução: pausa explícita baseada em playbackRequested, ticket ligado ao motor, sintonia e revisão; retoma apenas se ainda pertence ao pedido atual e nenhuma ação posterior a substituiu. Evitar toggle em decisões de lifecycle. Não terminar indiscriminadamente uma sintonia que deve apenas ficar pausada.

Aceitação: Home durante BUFFERING com segundo plano desligado; Play/Pause da notificação antes de regressar; novo canal/motor antes de regressar. A ação mais recente vence e não há som iniciado contra a opção escolhida.

### A2 — Passagem Exo → mpv admite continuação antiga com URL repetida — P1 localizado

Evidência: vendor/OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt:2435–2449.

revertToMpv guarda URL/posição, desativa Exo, espera 600 ms e verifica apenas se a URL ainda coincide. A → B → A durante essa janela deixa passar a continuação antiga, que recarrega com posição anterior e resetRetries=false.

Âmbito real: passagem de diferidos/legendas de imagem para mpv. Não apresentar como causa demonstrada do zapping de direto ExoPlayer.

Solução: validar geração, revisão e propriedade da passagem antes de modificar/recarregar. Cancelar job antigo ao substituir a sintonia. O caminho advanceAfterNaturalEnd já oferece um padrão local de guardas.

Aceitação: scheduler com A → B → A, pausa, stop e novas opções da mesma URL durante a espera; nenhum carregamento antigo é executado.

### A3 — Callbacks Exo de diferidos sem identidade explícita — P2, risco a testar

Evidência: player-core/.../ExoSubtitleEngine.kt:213–269,327–332; OwnTVPlayer.kt:1632–1700. LivePreviewEngine.kt:1490–1534 já tem validação de propriedade.

Há listener/callbacks partilhados que escrevem erro, buffering, faixas e posição, sem token por start/player. Ausência da guarda está verificada; entrega de eventos tardios pelo Media3 não foi demonstrada.

Solução: ticket por início/instância, invalidado antes de stop/release; respostas e jobs de fallback validam a propriedade também depois de cada suspensão.

Aceitação: injetar erro, tracks, first-frame e posição de A após iniciar B; todos são ignorados. Preservar modos apenas Exo/apenas mpv.

## Lote B — backup e armazenamento

### B1 — Cancelamento/erro pode deixar backup ocupado e temporários — P1/P2

Evidência: app/.../settings/BackupViewModel.kt:106–115,123–137,146–151,163–181; core/.../backup/BackupManager.kt:96–97,304,546–547.

busy=true não tem finally. Cancelamento relançado pelo Core ou exceção de leitura pode saltar reposição e limpeza. A cópia usa mapCatching e precisa de política de cancelamento coerente. delete/deleteRecursively correm no Main após regressar dos métodos suspensos.

Solução: try/finally, relançar CancellationException, estados no Main e limpeza determinística em IO; usar NonCancellable apenas para a limpeza necessária. Manter ficheiro de importação quando for preciso repetir a senha. Cache residual pode conter dados de fontes; o conteúdo concreto depende das secções/proteção selecionadas.

Aceitação: cancelar geração/cópia, URI indisponível, ficheiro de inspeção desaparecido; nova operação disponível, temporários limpos, StrictMode sem I/O destes caminhos no Main.

### B2 — Pedido de exportação perde-se após recriação durante SAF — P1

Evidência: SettingsBackupPage.kt:77–85; recordings/RecordingsScreen.kt:78–94.

Contexto está em remember. Android pode restaurar o callback do seletor sem restaurar os parâmetros necessários; a URI recebida pode ser ignorada. Rotação simples não prova o bug: o Manifest trata orientação e outras mudanças. Testar recriação efetiva/morte do processo.

Solução: guardar identificador, tipo e parâmetros mínimos recuperáveis; resolver gravação por ID; não guardar senha do backup em texto simples no estado salvo. Se o processo morreu, pedir novamente a senha ou mostrar cancelamento claro.

Aceitação: ActivityScenario.recreate e morte do processo enquanto seletor está aberto; operação concluída ou retomada explicitamente, nunca silêncio.

### B3 — Eliminação física de gravação sem confirmação — P2, usabilidade

Evidência: RecordingsScreen.kt:390–394; RecordingsViewModel.kt:157; core/.../recording/RecordingManager.kt:672–690.

Solução: confirmação curta com nome e efeito real. Não prometer Desfazer se o ficheiro já foi eliminado. Aceitação: cancelar/confirmar e tratar gravação ativa.

## Lote C — Guia correto, responsivo e sem trabalho invisível

### C1 — Primeira atualização consolidada pode ser descartada — P2

Evidência: guide/GuideViewModel.kt:664–675,936.

debounce(10s).drop(1) elimina a primeira emissão já consolidada. Uma sincronização nos primeiros segundos pode fazer essa emissão conter mudanças reais, deixando as linhas antigas.

Solução mínima: ignorar a emissão inicial antes de debounce, uma única vez na cadeia externa. Não repetir drop dentro de cada troca de fontes e não deduplicar apenas pelo COUNT: conteúdos podem mudar sem alterar total.

Aceitação: emissão inicial em 0s, mudanças 2/5/9s, invalidação uma vez aos 19s; fontes vazias → fontes reais; edição com número igual de programas; arranque sem mudanças não invalida desnecessariamente.

### C2 — Loading/Error aparecem como vazio — P2

Evidência: GuideGrid.kt:156–160,185–191; live/LiveScreen.kt:210–257; GuideGrid.kt:122–138.

programmes=null significa consulta pendente, mas mostra sem guia. Listas paginadas não expõem loadState/retry nestes caminhos: erro pode parecer lista vazia.

Solução: Loading/Ready/Empty/Error por linha/lista; indicador discreto e Tentar novamente para Paging.retry. Repetir página não é atualizar catálogo no fornecedor.

Aceitação: consulta atrasada 300–800 ms e PagingSource que falha na primeira/página seguinte; vazio apenas depois de leitura concluída; erro recuperável explícito.

### C3 — Coletores internos do Guia continuam sem interface — P2

Evidência: GuideViewModel.kt:653–687; GuideScreen.kt:88–104.

collectAsStateWithLifecycle da UI não suspende os viewModelScope.launch permanentes que contam programas/invalidação. Custo real depende de o ViewModel continuar no back stack. Relaciona-se com a auditoria CPU anterior; é persistência de trabalho invisível, não nova arquitetura a inventar.

Solução: subscrição/visibilidade para trabalho exclusivo do Guia, invalidando uma vez na retoma. Não suspender sincronização geral, gravações ou lógica funcional de reprodução.

Aceitação: abrir Guia → sair para leitor/definições → atualizar EPG; contar SQL e confirmar ausência de reconstruções do Guia oculto e atualização única ao regressar.

### C4 — Linhas cronológicas têm limites diferentes — P2, hipótese forte de layout

Evidência: GuideGrid.kt:80,104,184–209.

Um ScrollState é partilhado pelo cabeçalho diário e por linhas cujo conteúdo termina cedo ou é só texto vazio. Há áreas roláveis a publicar limites diferentes; saltos/limitação de offset exigem reprodução visual.

Solução: largura temporal uniforme, incluindo espaço final e linhas vazias; avaliar um único proprietário horizontal. Não refazer o grid inteiro antes do teste mínimo.

Aceitação: programa curto, linha até meio do dia e linha vazia; chegar às 23h e fazer scroll vertical sem salto de hora/offset.

## Lote D — robustez condicionada e medição

- Cache mpv: crescimento residual pode impedir o contador local de stall de atingir o limite. Avaliar após reprodução já iniciada, distinguindo watchdog inicial. Testar cache saudável, imóvel e crescimento mínimo prolongado; eventual orçamento monotónico deve considerar défice/progresso acumulado e não voltar a penalizar pré-buffer normal. Risco, não congelamento comprovado.
- LL-HLS: timeout live mpv de 10s não tem a exceção de atualização bloqueante que ResponseDeadline aplica no Exo a _HLS_msn. Testar fonte/fixture antes de mudar política; query pode surgir em playlists internas. Não reverter globalmente HLS clássico.
- Quotas: MediaStorageBudget.kt:28–109 reconcilia ficheiros sob mutex de escritores. Instrumentar duração, quantidade de ficheiros e espera em SAF lento antes de alterar atomicidade/reservas.

## Melhorias e inovação úteis, depois das correções

1. Diagnóstico resumido da sessão: aproveitar o ring buffer limitado já existente em LiveDiagnosticsLog, com versão, motor, identidade, buffer, HTTP/decoder e sequência de eventos sanitizados. Não criar um segundo logger nem abrir streams extra para testar o fornecedor. Acrescentar uma síntese que explique observado versus desconhecido.
2. Medição antes/depois: baselineprofile já existe. Complementar com Macrobenchmark de arranque/scroll/Guia e percurso estável por seletores de teste, medindo frames/CPU em release num dispositivo. Não apresentar o gerador de Baseline Profile como prova de desempenho.
3. Operações de ficheiro com estado claro: preparar → copiar → concluir/retomar/cancelar; progresso sem popup permanente. Surge da correção SAF/backup, evitando mais opções dispersas.

## Não recomendado agora

- Mais buffer global, delay obrigatório em todo zapping, novos motores ou recuperação infinita.
- Ativar software/H.264 especial em todos os canais saudáveis.
- Alterar ganho interno de áudio.
- Reintroduzir favoritos, últimos canais, filmes/séries, meteorologia ou PiP.
- Apagar bibliotecas/dados só porque a interface está oculta.
- Assumir que estes achados explicam todos os bloqueios anteriores, um 403 do fornecedor ou cortes específicos de codec/firmware.

## Fontes oficiais e limites

- [Activity Result](https://developer.android.com/training/basics/intents/result): estado adicional da operação precisa de ser salvo/restaurado separadamente do callback.
- [Guardar estado](https://developer.android.com/topic/libraries/architecture/saving-states): distinguir sobrevivência do ViewModel e recriação/morte do processo.
- [Eventos Media3](https://developer.android.com/media/media3/exoplayer/listening-to-player-events): BUFFERING e playWhenReady não equivalem a isPlaying.
- [Desempenho Compose](https://developer.android.com/develop/ui/compose/performance): melhorias devem ser avaliadas com percursos e medições; listas usam chaves estáveis.

Esta análise não repetiu os 1.629 testes da integração anterior nem mediu CPU/áudio/fluidez. O estado anterior de validação consta em IMPLEMENTACAO_CORE_1_0_66_LIBMPV_2026_10_3_2026_10_11.md. Cada lote proposto requer os seus testes de regressão antes de implementação seguinte.
