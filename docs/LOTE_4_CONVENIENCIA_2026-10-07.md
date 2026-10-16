# Lote 4 — Previsão de espaço, capítulos e diferidos

Data: 07/10/2026. Implementado na OwnTV Mobile e no Core incorporado em vendor/OwnTV_Core. Este lote corresponde ao plano de conveniência acordado após os lotes de gravações/guia, notificação/playlist local e backup/EPG/M3U. Não é o lote de efeitos visuais da análise anterior de CPU.

Sem alterações na OwnTV-main ou no Core externo. Sem APK, migração de base de dados ou alterações de buffer, codecs e transporte HLS.

## Previsão de espaço para gravação

No programa aberto no Guia TV, antes do botão de gravação:

- Intervalo aproximado em GiB e origem da estimativa.
- Mediana de até cinco gravações recentes da mesma fonte e canal com duração de media confirmada de pelo menos 30 segundos; descartadas amostras falhadas e velocidades inválidas.
- Margem indicativa de 70–150% em torno da mediana. Não é um intervalo estatístico de confiança.
- Sem histórico utilizável: intervalo genérico de 2–40 Mbit/s, explicitamente identificado como indicativo e passível de ser ultrapassado. Não é uma garantia nem um limite imposto ao vídeo.
- Incluída a duração agendada com margens anteriores/posteriores; num programa do arquivo, usada a duração do programa.
- Espaço disponível limitado pelo menor valor entre espaço físico acima da reserva e quota restante. É descontada uma reserva estimada das gravações pendentes, sem reservar fisicamente blocos de disco. A gravação correspondente ao programa selecionado é excluída para evitar contagem dupla.
- Capacidade desconhecida permanece desconhecida; não é interpretada como zero disponível.
- Aviso quando o limite superior estimado ultrapassa o espaço disponível. Não bloqueia o agendamento nem elimina gravações: o tamanho e o destino podem mudar, e o orçamento de armazenamento existente continua a controlar a captura real.

A página Gravações mostra também a capacidade disponível após reserva e quota, separada do espaço livre físico.

Sem pedidos de teste ao fornecedor, nova amostragem contínua de bitrate ou trabalho por fotograma. A consulta da previsão ocorre ao abrir o programa e quando muda a lista de gravações. Gravação imediata pelo botão do leitor continua com o comportamento existente; a previsão prévia foi integrada no agendamento pelo guia.

Limitações: bitrate variável, mudança de resolução, gravações movidas para outro volume e agendas concorrentes podem tornar a previsão conservadora ou imprecisa. A duração estimada não garante que o fornecedor entregue todo o programa.

## Capítulos baseados no EPG

- Ao criar uma gravação, são guardados títulos e limites dos programas conhecidos que intersectam a janela; respeitados associação/fallback de EPG e desfasamento configurado.
- Num diferido, usada a janela original do programa, não a hora em que o download começou.
- Snapshots de gravações pendentes são atualizados quando o guia atualiza os agendamentos. Gravações concluídas conservam o snapshot guardado, independentemente da expiração do guia.
- Metadados em ficheiros privados, ligados à identidade de gravação, perfil, fonte e canal; sem URLs ou credenciais. Escrita por temporário, leitura limitada e validação de identidade/formato. Eliminados quando a gravação é eliminada pelo gestor.
- O leitor interno apresenta “Programas na gravação” quando existem capítulos utilizáveis. Escolher um programa procura a posição na gravação atual, sem parar/reabrir o canal ou criar ligação ao fornecedor.
- Posições explicitamente aproximadas; ajustadas ao começo efetivo de uma captura tardia e limitadas à duração conhecida. Gravações com lacunas ou duração em falta conhecida não apresentam atalhos baseados numa correspondência temporal que já não seria válida.
- Até 512 capítulos, títulos individuais até 16 KiB e leitura até 9 MiB. Falha nos metadados opcionais não impede gravar. Leitura dos capítulos precede a aquisição do servidor local de reprodução multipartes.

Limitações: relógio do fornecedor, primeiro segmento recebido e atrasos do EPG podem deslocar os saltos. Não são capítulos de precisão de fotograma. O leitor externo não recebe estes capítulos. Gravações antigas sem snapshot não ganham capítulos por suposição. Os metadados permanecem privados da app; não foram adicionados ao ZIP portátil nem ao backup de configurações, que já exclui as gravações.

## Informação sobre diferidos

O programa aberto no guia distingue:

- EPG existente, mas sem capacidade de arquivo declarada;
- programa futuro;
- programa fora do limite de consulta da app;
- programa dentro da janela declarada, indicando a duração declarada;
- programa fora da janela declarada, mas ainda possível de tentar;
- arquivo declarado sem duração conhecida.

Mantidas as tentativas deliberadas para programas mais antigos que metadados potencialmente desatualizados. Nenhuma mensagem de disponibilidade confirmada antes de abrir. Não se converte um pedido de programa antigo em reprodução do direto. As mensagens de falha de resolução e erros reais do leitor continuam no fluxo existente; metadados isolados não permitem diagnosticar rede ou afirmar que um programa foi removido pelo fornecedor.

## Validação

Validação final: BUILD SUCCESSFUL.

- Compilação Kotlin standardRelease: aprovada.
- lintVitalStandardRelease, incluindo recursos/i18n configurados: aprovado.
- Testes unitários da app: 62, todos aprovados.
- Testes unitários do Core: 1084, todos aprovados.
- Total: 1146; sete testes novos neste lote.
- Verificação do diff dos ficheiros principais: sem erros de espaços/conflitos.

Não foi gerado APK nem executado benchmark ou teste instrumentado em dispositivo.

Testes novos verificam cálculo a partir de duração de media, isolamento por canal/fonte, quota/reserva/agendamentos, armazenamento desconhecido, saturação numérica, começo tardio, supressão de atalhos com lacunas, identidade e corrupção dos metadados persistidos, origem temporal do arquivo e distinção das janelas de diferidos.

## Verificações em dispositivo

1. Abrir programa futuro/atual/passado no guia; conferir previsão e aviso com quota baixa, armazenamento interno e pasta externa.
2. Gravar um intervalo abrangendo vários programas com EPG; deixar o guia expirar/atualizar e verificar os títulos guardados.
3. Reproduzir e procurar entre capítulos; confirmar som/imagem, limites de procura e ausência de novo pedido ao fornecedor.
4. Reproduzir gravação iniciada tarde, diferido descarregado e captura com lacunas; confirmar os atalhos disponíveis e ausentes conforme a política.
5. Testar no guia canal sem arquivo, arquivo desconhecido e programa fora da janela declarada, confirmando que as tentativas permitidas continuam acessíveis.
6. Validar apresentação, toques e foco numa box e num smartphone. Não foi executada validação visual em dispositivo nem medida melhoria de CPU/tempo neste lote.
