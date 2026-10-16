# Lote B — backup, exportação e eliminação de gravações

Data: 15/10/2026. Estado: implementado na Mobile e no Core privado. Compilação, testes e lint aprovados; sem APK e sem validação física.

Âmbito: B1/B2/B3 da AUDITORIA_MOBILE_COUNCIL_2026_10_12.md. OwnTV-main e Core externo não foram editados. Reprodução, motores, buffer, latência, volume e esquema da base de dados mantêm-se.

## B1 — cancelamento, erros e temporários

- BackupViewModel reserva a operação imediatamente, evitando dois arranques por toques rápidos, e repõe busy em finally.
- CancellationException é relançada; erros normais originam mensagem de falha e deixam a interface disponível para uma nova tentativa.
- Importação, exportação e limpeza usam pastas temporárias únicas. Todas as operações de cópia e eliminação destes temporários correm em IO.
- A limpeza usa NonCancellable apenas onde é necessária para concluir a remoção; cancelamento é verificado entre blocos da cópia.
- A criação de pastas também trata cancelamento no regresso de withContext, evitando perder uma pasta criada antes de o resultado chegar ao chamador.
- Importação passa a ser copiada e inspecionada sob propriedade do ViewModel, sem depender da corrotina do composable. O nome fornecido pelo fornecedor do documento não decide o caminho na cache.
- Um ficheiro à espera de nova palavra-passe é mantido apenas enquanto esse passo está pendente; cancelar/terminar/fechar o ViewModel pede a sua limpeza.
- Na inicialização do fluxo de backup, são removidas pastas de transferências de processos anteriores. Pastas do processo atual são preservadas. As eliminações validam o âmbito gerado pela app.
- Cópia de backup incompleta por erro/cancelamento tenta remover o documento de destino que chegou a ser aberto. Não altera o backup de origem escolhido para importação.

Limite: encerramento forçado do processo não executa finally. A limpeza de cache abandonada ocorre na próxima inicialização deste fluxo. Não foi criada uma fila persistente que retome cópias externas interrompidas; um destino parcial pode precisar de ser removido/recriado após uma paragem forçada.

## B2 — pedido SAF recuperável

- Os parâmetros não secretos são guardados no SavedStateHandle como JSON versionado: tipo da operação, identificador único, perfis, secções ou ID de gravação e destino, quando conhecido.
- Não se guardam palavra-passe do backup, URLs de fontes, credenciais ou snapshots completos de gravações neste estado.
- A palavra-passe de exportação fica apenas na memória do ViewModel e ligada ao identificador do pedido. Se o processo morrer e faltar esse segredo, o fluxo pede novamente a palavra-passe antes de escrever.
- O pedido não é substituído por outro enquanto o seletor está pendente; o callback consome o pedido correspondente. Um callback sem contexto apresenta erro explícito.
- Há uma opção contextual para cancelar uma exportação pendente, evitando bloquear o fluxo quando o seletor não regressa.
- Exportação de gravação volta a consultar a linha por ID e valida perfil ativo, disponibilidade e tipo de ficheiro/partes antes de abrir o destino. Preserva a exportação e relocalização existentes.
- Koin recebe SavedStateHandle durante a construção dos dois ViewModels; o suporte foi confirmado nas fontes locais de Koin 4.2.2.

Android continua responsável por entregar o resultado e restaurar o estado da Activity. A recuperação real com fornecedores SAF precisa de teste no dispositivo. Não persistir a palavra-passe é intencional.

## Último backup concluído

Foi corrigido um problema de consistência do fluxo: o Core registava sucesso assim que gerava o ficheiro temporário, antes da cópia para o destino escolhido.

BackupManager.export ganhou recordCompletion=true por omissão, preservando os outros consumidores. A Mobile usa false ao preparar o ficheiro e regista data, tamanho, proteção e URI final depois de fechar o documento com sucesso. Falha na geração/cópia não atualiza essa indicação como se o destino estivesse concluído.

## B3 — confirmação de eliminação

- Apagar no menu abre uma confirmação com o título da gravação e o aviso de irreversibilidade.
- Se estiver a gravar, o texto explica que a ação para e elimina o ficheiro.
- Cancelar a confirmação não chama o gestor de gravações.
- Confirmar mantém as proteções já existentes do gestor; o ViewModel verifica ainda o perfil ativo.
- Não foi acrescentado um Desfazer fictício depois da eliminação física.

## Validação concluída

| Verificação | Resultado |
|---|---:|
| Testes Mobile | 89, zero falhas/erros/ignorados |
| Testes Core privado | 1.114, zero falhas/erros/ignorados |
| Testes player | 448, zero falhas/erros/ignorados |
| Total | 1.651 |
| Compilação Kotlin Standard Release | Passou |
| lintVitalStandardRelease | Passou |
| Inventário de literais | Passou; baseline Mobile continua sem dívida nova |
| git diff --check | Passou |

Onze testes novos: quatro para identidade/serialização/consumo do pedido e sete para ficheiros, cópia, cancelamento, criação interrompida, limites de eliminação e reposição da operação. As verificações de ficheiros usam temporários reais de teste. A recuperação de parâmetros é testada por reconstrução do controlador sobre o estado serializado, não por matar um processo Android.

O formato do backup continua v25. Não foram removidas secções, fontes ou configurações. Os testes existentes do Core continuam a validar os formatos suportados; a lógica de conteúdo do backup não foi substituída.

## Testes manuais pendentes

1. Exportar backup protegido e sem palavra-passe; restaurar fontes/configurações e confirmar as opções por canal, prioridades, tempos e modos da Mobile.
2. Recriar efetivamente Activity/processo enquanto o seletor de destino está aberto; confirmar recuperação do pedido e pedido de palavra-passe quando necessário. Rotação normal não prova este cenário, porque o Manifest já trata orientação.
3. Cancelar o seletor, cancelar o pedido recuperado, escolher armazenamento indisponível e testar um fornecedor de nuvem. A interface deve permitir nova tentativa e não indicar sucesso prematuro.
4. Exportar gravação simples e gravação com partes; verificar reprodução do resultado, perfil e comportamento existente de localização/cópia.
5. Cancelar e confirmar eliminação; repetir com gravação ativa. As proteções do gestor podem recusar uma eliminação em utilização.

Não foram medidos CPU/RAM nem executados testes físicos de SAF, layout, permissões ou fornecedores de documentos. Nenhum APK foi gerado.

## Continuidade

- Lote A: implementado anteriormente; ver LOTE_A_ESTADO_REPRODUCAO_2026_10_15.md.
- Lote B: implementado neste pedido.
- Próximo lote C: atualização do EPG, estados Loading/Empty/Error, Paging retry e trabalho do Guia fora do ecrã.
- Lote D: compatibilidade e medições condicionadas continuam propostas.

Cópias anteriores dos ficheiros principais: C:/Users/renat/AppData/Local/Temp/owntv-before-loteB-20261015.
