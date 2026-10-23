# Mobile 1.1.0 / Core 1.0.68 — integração seletiva

Data: 18/10/2026. Autorização: «avança com a implementação das recomendações».
Projeto: `C:/Users/renat/Downloads/OwnTV_Mobile-main/OwnTV_Mobile-main`.

## Âmbito e decisão do council

Três revisões especializadas de dados/EPG, streaming/codecs e interface/desempenho; integração e revisão cruzada central. Referências fixas: Mobile `52c35d0b108fc2ae195e682612bf1c548e662bc3`, Core `c14e944b8d17e5cbb559d116afb30dc3b34864cb`. Consultar a [análise anterior](ANALISE_MOBILE_1_1_0_CORE_1_0_68_2026_10_18.md).

Integraram-se deltas comprovados, preservando os lotes anteriores de backup, perfis, gravação RAW/DASH/SAF e identidade da reprodução. Não houve substituição do projeto ou Core, atualização do binário mpv, criação de APK, assinatura ou publicação. A TV e o Core externo permaneceram fora das edições. O Core usado é `vendor/OwnTV_Core` via `includeBuild`.

## Interface, CPU e memória

- Live: páginas de 60, início de 120 e orçamento de 400 itens. Guia: páginas de 40, início de 80 e orçamento de 300. São limites das páginas carregadas, não do catálogo ou dos dias EPG; Paging pode exceder temporariamente o orçamento para cumprir as suas garantias de prefetch.
- Grupos de versões: mantêm a identidade do representante por geração, em vez de um indicador consumido uma única vez. Uma página descartada e relida não apaga o grupo. O índice leve de chaves/IDs permanece durante essa geração; o limite refere-se às entidades paginadas.
- As caches agora/seguinte já eram limitadas (Live 512, Guia 240). Foram preservadas; não se aumentou a cache do Guia para 500.
- Relógio e posição do HUD são lidos nas folhas que os desenham. O timer de guardar posição VOD só existe com um item elegível; as gravações explícitas de posição ao parar/trocar mantêm-se.
- Cabeçalhos, agrupamentos e listas auxiliares: menos consultas por linha, consultas de metadados em lote, tipos de conteúdo e recomposição localizada. A disponibilidade de gravações continua dependente da visibilidade. A informação de espaço livre é atualizada a cada dez segundos durante uma transferência ativa/subscrita e nas alterações de destino/estado, evitando tanto consultas por tick como um valor imóvel até ao fim.
- Imagens: singleton Coil e cliente derivado dedicado a imagens, mantendo o cliente do streaming. RAM de 8–48 MiB conforme heap/dispositivo; disco até 32 MiB em low-RAM ou 96 MiB nos restantes, proporcional ao espaço livre, desativado com menos de 64 MiB livres. Sem crossfade ou pré-carga de posters.
- Android 14+: amostragem de memória a cada cinco segundos apenas com reprodução/buffering solicitado. Pausa/stop cancela o timer. Atua uma vez por episódio de pressão, permite escalada e ignora UI_HIDDEN.

## Guia — conclusão dos pontos G1–G6

- Observação de invalidação real das tabelas, incluindo edições de título/horário com a mesma contagem. Retirada a combinação `debounce().drop(1)` que podia absorver a primeira alteração.
- Alterações em lote estabilizam durante dez segundos. Com o Guia oculto, a atualização fica pendente; no regresso é aplicada uma vez. Pedidos pendentes e estatísticas respeitam visibilidade, cancelamento e contexto.
- Estados distintos de carregamento, dados, vazio e erro, com Repetir nas linhas e nas falhas de Paging. Uma falha de leitura não é apresentada imediatamente como ausência de EPG.
- Cache concluída só é reutilizada para o mesmo perfil/fontes/desvio/janela, inclusive antes de o coletor de invalidação executar.
- Fragmentos temporais sem sobreposição: um programa curto pode aparecer dentro de um longo; o toque conserva a entidade e o início original usados no pedido de diferido. Só são compostas células próximas da janela visível.
- Cabeçalho e linhas têm a mesma largura temporal total, incluindo linhas vazias e lacunas finais. A cronologia de um canal é reposta quando muda o perfil, fontes, categoria ou pesquisa.
- Removida a eliminação SQL de programas entre fontes. A seleção na leitura usa a fonte do canal como preferência, proximidade temporal, cobertura forte e sinopses compatíveis quando disponíveis. As projeções leves continuam a omitir sinopses; não se passou a carregar todas as descrições na grelha. Os dados já apagados por versões antigas voltam apenas com nova sincronização da fonte.
- XMLTV aceita precisão de minutos/segundos, UTC/Z e offsets suportados, preservando a tolerância do formato legado. O spool calcula o hash canónico sem perder metadados, bloqueios ou limites existentes.

## Reprodução e diferidos

- Surround reconfigura o motor proprietário. Exo ativo não provoca reload de uma cópia mpv.
- Reconstruções Exo têm job cancelável e invalidam arranques/superfícies/legendas obsoletos, preservando as revisões de item/comando do lote anterior.
- Retenção de áudio por identidade; idiomas ISO equivalentes sem confundir regiões/scripts conhecidos. Não reaparecem botões de áudio removidos.
- O formato HLS declarado é respeitado pelo adaptador mpv mesmo com URL sem extensão ou terminada em `.ts`.
- Retry explícito após desistência mpv recupera o endereço elegível; stop, novo conteúdo e release invalidam-no.
- Diferidos usam contexto tipado para conservar programa, posição, metadata, formatos/alternativas declarados e intenção de pausa. A renovação da URL verifica perfil, fonte, canal e revisão antes/depois. Não se inventaram alternativas TS/HLS.
- Fallback e handover locais já eram mais restritivos que alguns ramos upstream: foram conservados. Sem watchdog novo, espera global adicional ou software global acima de 1080p.
- Volume interno permanece a 100%; a alteração de volume pertence ao Android/dispositivo.

## Dados, fontes e novas opções

- Migração privada **51 → 52**, adicionando `sources.serverTimeZone` e `channels.catchupCorrectionMin`, sem substituir o schema local pelo upstream49. Backups preservam os campos; ausências legadas continuam compatíveis.
- Definições → Fontes e guia → Programação retroativa: opção **Automático (servidor da lista)** global ou por lista. Usa fuso válido anunciado pelo painel e fallback do dispositivo. O modo Dispositivo continua predefinido; modos manuais e correções existentes mantêm prioridade. A correção do pedido não altera a hora mostrada no Guia.
- Templates M3U suportam novos placeholders de relógio e formatos. `catchup-correction` é aplicado ao pedido, separadamente da apresentação.
- Uma nova ligação M3U `get.php` reconhecível pode ser importada como Xtream após validação limitada da API. Preserva UA/Referer e âmbito de canais; não converte listas existentes silenciosamente. Probe com corpo até 1 MiB e cancelamento/prazo de seis segundos; uma resolução DNS nativa bloqueada pode depender do sistema para terminar.
- Pesquisa: filtros Tudo/TV/Programas e resultados progressivos limitados, sem consultar/apresentar filmes e séries nesta rota. O limite é de 1.000 resultados por tipo com pesquisa de candidatos também limitada; não é uma promessa de busca ilimitada.
- Canal anterior/seguinte: segue a lista visível em ordem de fornecedor/A–Z/personalizada, com ocultação e grupos. Resolve uma fotografia completa até 5.000 canais; acima desse limite não publica uma lista truncada e mantém o comportamento alternativo já existente. O índice de navegação tem orçamento separado das páginas da lista.
- Corrigidos o preenchimento inicial da chave TMDB, o tipo de teclado de password/PIN e a deslocação de seletores longos, nas rotas existentes.

## Não transplantado e porquê

- Não se copiou o novo subsistema de timeshift local: a classe local comparada não tem consumidor ativo nesta Mobile; a recuperação de segmentos assinados já existe e possui testes. O caminho ativo de arquivo/direto recuado recebeu renovação de URL com contexto.
- Sports, Stage, ícones/fontes novos, ClearKey inline opcional e política de retoma de cópia local ficam fora deste lote.
- Sem PiP, animações, boost, novos favoritos/histórico, filmes/séries ou downloads paralelos.
- Funcionalidades já existentes, como gravação manual e verificação de armazenamento, não foram duplicadas.

## Validação

Concluída: **BUILD SUCCESSFUL** na execução final, incluindo a última alteração. Sem APK.

| Suite | Testes | Falhas/erros/ignorados |
|---|---:|---:|
| Mobile | 116 | 0/0/0 |
| Core privado | 1.180 | 0/0/0 |
| Reprodutor | 459 | 0/0/0 |
| **Total** | **1.755** | **0/0/0** |

Totais contados nos XML finais, sem somar execuções repetidas. Compilação Kotlin standardRelease, testes unitários, compilação dos testes Android do Core e lintVitalStandardRelease aprovados. Lint vital: `No issues found.` Inventários i18n aprovados sem aumentar baselines (Mobile0/Core311); recursos XML válidos e git diff --check sem erros. Migração51→52 exercitada pelos testes JVM/SQLite; testes Android/Room compilados, não executados.

Registo resumido: [validação JSON](validacao/MOBILE_CORE68_20261018.json). Nenhum dispositivo apareceu em adb devices. Não se executaram medições de CPU/RAM/FPS, reprodução física, SAF real ou navegação por toque/tablet.

Foram corrigidas durante a validação referências locais de integração e atualizadas as expectativas antigas para o novo campo do backup e o offset XMLTV com dois pontos. Nenhum teste foi desativado para obter este resultado.

## Aceitação em smartphone/tablet

1. Percorrer uma lista extensa, regressar ao canal atual e testar ordem A–Z/personalizada, versões agrupadas e ocultação.
2. Abrir o Guia em portrait/landscape, percorrer até ao fim do dia numa linha vazia/curta, abrir programa sobreposto e testar erro/Repetir.
3. Sair do Guia durante sincronização e voltar; mudar perfil/fontes e confirmar que não aparece a programação do perfil anterior.
4. Abrir direto e diferido, pausar, repetir/renovar ligação e mudar rapidamente de canal. Confirmar HLS estrito, motor exclusivo, áudio e volume controlado pelo Android.
5. Testar importação `get.php`, UA/Referer, fuso automático por lista e backup/restauro sem duplicar correções de uma hora.

Sem benchmark físico não se atribui percentagem de redução de CPU/RAM nem se garante ausência de bloqueios do fornecedor.
