# Lote 1 — Gravações e guia de TV

Implementado em OwnTV_Mobile-main/OwnTV_Mobile-main e no Core incluído em vendor/OwnTV_Core. Os projetos OwnTV-main e OwnTV_Core externos a esta pasta não foram alterados.

## Gravações

- Reprodução e botões usam RecordingIntegrity.canPlay: inclui parciais recuperáveis e recusa ficheiros vazios, ausentes na configuração ou com finalização falhada.
- Abertura relê a gravação e valida o perfil ativo. Toques duplicados não enfileiram vários arranques.
- Gravações multipartes abrem pelo servidor local existente do Core. O tuner mantém o servidor durante a reprodução e liberta-o quando esta é substituída ou parada. Cancelações durante a transferência de propriedade e falhas de abertura externa libertam o recurso.
- O regresso do leitor externo liberta o servidor e o marcador de entrega. O arranque externo também encerra uma transmissão Cast anterior deste tuner.
- Exportação multipartes produz um ZIP completo: index.owntv.json, index.m3u8 com caminhos relativos, segmentos e init.mp4 quando aplicável. Para reproduzir fora da app, extrair o ZIP e abrir index.m3u8 num leitor compatível; ZIP não é um ficheiro de vídeo único nem acrescenta importação de pacotes à app.
- Exportação multipartes mantém o original. A cópia usa um buffer limitado, valida tamanhos, conserva descontinuidades e não guarda URLs do fornecedor. Falhas/cancelamentos tentam eliminar o destino incompleto. O pacote fica protegido contra limpeza por quota durante a cópia.
- Exportação de ficheiros únicos mantém o funcionamento existente de mudança de localização após acesso persistente; verifica o tamanho copiado e comunica recusas de mudança.
- A página abre em Todas, agrupa por estado, mostra contagens, preserva a seleção durante recriação e usa mensagens específicas para filtros vazios.
- Agendamentos mostram Hoje/Amanhã/data, hora no formato do sistema e ano quando necessário. Erros de operação são apresentados na página.

## Guia de TV

- Agora só se aplica ao dia atual. Qualquer dia anterior ou futuro usa a cronologia selecionada.
- Cache de programas atuais expira no fim do programa. Resultados vazios usam TTL de 30 segundos, antecipado se o próximo programa começar antes.
- Leituras antigas são canceladas/invalidadas quando mudam os canais visíveis, perfil, fonte, personalizações ou desfasamento do EPG.
- Cache limitada a 240 canais. Atualização usa o relógio existente de 30 segundos, ativo apenas enquanto a interface está visível.
- Datas e janelas recalculadas na meia-noite, mudança de fuso e regresso à página. Janelas usam dias de calendário, incluindo dias com 23/25 horas.

## Verificação manual em dispositivo

1. Abrir uma gravação parcial recuperável; confirmar imagem, áudio e procura temporal. Uma parcial com finalização falhada não deve oferecer reprodução.
2. Abrir uma gravação multipartes e procurar entre partes; parar e abrir outro conteúdo; confirmar que se pode depois eliminar a gravação.
3. Exportar multipartes; extrair ZIP e abrir index.m3u8. Testar cancelamento/falta de espaço sem perder o original.
4. Testar leitor externo, ausência de leitor compatível e regresso à app.
5. Abrir gravações quando só existem concluídas/agendadas, verificar filtros e datas.
6. No guia, testar hoje/amanhã/ontem, mudança de programa, regresso do segundo plano e mudança de dia/fuso.

Não foi gerado APK. Não foram efetuados testes em dispositivo físico nem medições de CPU/RAM. O lote não altera buffers, codecs ou as configurações globais de HLS dos canais.

## Validação automatizada

Compilação Kotlin standard release e lintVitalStandardRelease: BUILD SUCCESSFUL.

- app/build/test-results/testStandardDebugUnitTest: 57 testes; 0 falhas; 0 erros; 0 ignorados.
- vendor/OwnTV_Core/core/build/test-results/testDebugUnitTest: 1054 testes; 0 falhas; 0 erros; 0 ignorados.

Regressões adicionadas: fronteiras de programas e TTL, meia-noite e mudanças de hora de Lisboa, datas de agendamentos, ZIP completo TS, playlist fMP4 portátil, segmentos ausentes e exportação interrompida com repetição segura.
