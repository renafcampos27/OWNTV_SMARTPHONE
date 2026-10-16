# Lote 2 — Notificação e playlist de gravações locais

## Âmbito

Alterações na OwnTV Mobile e no Core incluído em vendor/OwnTV_Core. Sem alterações à OwnTV-main nem ao Core externo. Sem geração de APK.

## Notificação de reprodução

- Nome, estado e controlos são publicados imediatamente, antes de carregar o logótipo.
- A imagem antiga é removida quando muda o URL do logótipo. Na ausência de imagem, mantém-se o ícone normal da notificação.
- O carregamento da imagem decorre numa tarefa própria, cancelada quando muda o URL.
- Cada pedido tem uma geração. Mesmo uma conclusão tardia de um pedido cancelado não pode substituir a imagem atual, incluindo a sequência A → B → A.
- Pausa/retoma ou atualização de metadados com o mesmo URL não reiniciam o pedido; a conclusão usa o estado mais recente.
- Erros de imagem deixam a notificação funcional. Não se repete um pedido falhado a cada pausa/retoma.
- Destruição do serviço cancela o trabalho e rejeita conclusões antigas. A publicação inicial em foreground continua síncrona.

## Playlist local de gravações em curso

- As durações e marcas de descontinuidade são formatadas uma vez por segmento confirmado.
- A playlist textual completa é guardada por revisão e reutilizada nos pedidos sem alterações.
- Publicação de um novo segmento, definição inicial da duração alvo e finalização invalidam a cache.
- O estado é capturado sob um bloqueio curto; a construção do texto fica fora do bloqueio utilizado para publicar e abrir segmentos.
- O texto de uma revisão antiga não pode substituir a cache de uma revisão mais recente.
- Mantém-se a playlist EVENT desde o primeiro segmento, a procura temporal, os sinais de descontinuidade, a inicialização fMP4 e ENDLIST na finalização. Não existe truncagem do histórico.
- A cache adiciona fragmentos de texto por segmento e uma playlist completa em memória. Reduz reconstruções repetidas, mas não constitui uma medição de redução de CPU/RAM.

## Testes de regressão

- Notificação imediata com imagem lenta; atualização de pausa antes da imagem chegar.
- Mudanças rápidas A → B → A, pedido antigo não cancelável, URL vazio, erro de imagem e destruição do serviço.
- Reutilização da mesma playlist, invalidação por novo segmento/duração alvo/finalização e rejeição de duplicados.
- Cronologia de 2 000 segmentos, sem perder o início nem as descontinuidades.
- Três leitores concorrentes com publicação de 401 segmentos e finalização; playlists consistentes e revisão final completa.
- Testes existentes de HTTP local, Range, TS/fMP4 e libertação dos ficheiros.

## Validação em dispositivo ainda necessária

1. Mudar de canal com logótipos lentos, verificar nome/estado corretos na notificação e no ecrã bloqueado.
2. Pausar/retomar durante o carregamento do logótipo e confirmar que uma imagem antiga não reaparece.
3. Reproduzir uma gravação em curso prolongada e procurar no início/meio/fim enquanto a gravação continua.
4. Confirmar TS e fMP4, finalização, paragem e libertação de ficheiros.

Este lote não muda buffers, codecs, volume, ligações ao fornecedor ou política de zapping. O ganho da playlist aplica-se à reprodução local das gravações em curso; não é uma correção geral de falhas HLS dos canais.

## Resultado automatizado

Compilação Kotlin standard release e lintVitalStandardRelease: BUILD SUCCESSFUL.

- App: 62 testes; 0 falhas; 0 erros; 0 ignorados.
- Core: 1058 testes; 0 falhas; 0 erros; 0 ignorados.

Não foram efetuados testes físicos nem benchmarks.
