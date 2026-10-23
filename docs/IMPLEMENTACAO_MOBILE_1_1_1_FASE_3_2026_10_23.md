# Mobile 1.1.1 — fase 3: superfícies, erros terminais e custo de diagnóstico

Data: 23/10/2026. Autorização: «avança com a próxima fase: fase 3». Estado: implementação e validação automática concluídas; aceitação física pendente.

## 1. Superfícies de vídeo

O Live Exo prepara cada origem apenas quando existe uma superfície visível válida ou o modo apenas áudio está ativo. A espera é suspensa por eventos, sem polling nem atraso fixo no canal saudável; tem teto de 5 s para uma superfície que nunca chega. Mudança de sintonia, pausa, release ou substituição do player invalidam/cancelam o trabalho antes da preparação. A espera é posterior ao encerramento HTTP local e anterior à criação da origem Media3; não faz pedidos de rede adicionais.

A recriação da vista invalida imediatamente a superfície anterior. O decoder novo não usa essa vista enquanto Compose monta a substituição. O regresso do modo áudio liga a superfície antes de reativar a faixa de vídeo; se a vista ainda não chegou, a reativação fica para o seu callback. Uma preparação sem superfície também mantém a faixa de vídeo desativada até esse ponto.

No mpv, `NativeSurfaceBinding` dá identidade a cada attach e conserva o dono efetivamente instalado. O attach passa para o executor de comandos, descarta pedidos ultrapassados e verifica a validade da vista. Antes de instalar outra superfície, liberta a referência nativa anterior: o wrapper JNI inspecionado cria uma nova GlobalRef no attach, sem limpar sozinho uma referência anterior.

A destruição da vista antiga liberta-a mesmo quando a substituta já foi pedida mas ainda está na fila. Depois de a substituta estar instalada, um callback antigo não a desliga. Os tamanhos também pertencem à superfície que os emitiu; callbacks/escritas ultrapassados são rejeitados.

**Contrato de destruição:** detach nativo continua síncrono, protegido pelo mesmo lock das operações de superfície, antes de devolver `surfaceDestroyed`. Não fica atrás da fila de comandos de rede, nem acrescenta uma espera de 2,5 s. Uma operação nativa de attach/detach já em execução ainda pode atrasar o callback: o código não promete um limite que o JNI não oferece. Os tempos de attach/detach ficam no diagnóstico detalhado. A documentação [SurfaceHolder.Callback](https://developer.android.com/reference/android/view/SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)) exige que o renderer deixe de tocar na superfície antes do regresso do callback.

Ficheiros: `NativeSurfaceBinding.kt`, `VideoOutputReady.kt`, `OwnTVPlayer.kt`, `LivePreviewEngine.kt`; na app, apenas o callback de tamanho em `VideoStage.kt`.

## 2. Erros terminais

Os ramos terminais do Live Exo convergem em `failLoad`: orçamento de reconexão esgotado, preparação/retry falhado, indisponibilidade de decoder, limite de espera do fornecedor e resgates de áudio/budget esgotados.

Um erro sobre reprodução atual confirmada continua a ser descartado e a sobreposição limpa. Paragens deliberadas por áudio irrecuperável ou proteção de descodificação por software conservam a sua autoridade, mesmo se a imagem ainda avançar.

Numa falha terminal real, a origem perde primeiro a propriedade dos callbacks; são cancelados a preparação, resolução de URL, HTTP e timers; o player faz stop + clearMediaItems antes de publicar ERROR. Conserva-se a identidade/metadados necessária ao botão de repetir. Uma nova sintonia volta a poder preparar normalmente. As guardas existentes impedem que callbacks de uma origem anterior parem a nova.

Os erros recuperáveis mantêm os orçamentos e recuperações existentes. Não foi importada a escada upstream que poderia alterar o motor exclusivo ou introduzir TS onde só HLS está autorizado.

## 3. Menor custo de execução

- `BufferUiSample` publica o buffer mpv em segundos inteiros e atualiza-o com a posição. O cache exato em Double continua disponível para pre-buffer e recuperação; não se alteram limiares de arranque/retoma.
- `NativeRead` rejeita consultas ainda em fila quando o consumidor expirou/cancelou, o motor foi substituído ou mudou a geração. Também rejeita resultados de uma geração antiga. Chamadas JNI já iniciadas não são interrompidas por este helper.
- Leituras opcionais extensas de estatísticas/timing deixam de ocorrer em cada abertura com diagnóstico desligado. As verificações funcionais do decoder continuam.
- Com diagnóstico detalhado ativo, há amostras mpv espaçadas por 5 s de cache e perdas de fotogramas; sem sondas de rede nem uma nova rotina de recuperação. O ciclo fica cancelado quando o diagnóstico é desligado. As amostras pertencem à geração atual.
- Regex de ocultação de credenciais compiladas uma vez, mantendo as mesmas regras de query, userinfo e caminhos Xtream/diferidos.
- A fila limitada de 512 entradas, escrita em lotes, rotação limitada e ring imediato do `DiagnosticWriter` foram preservados. `LiveDiagnosticsLog.detail` só constrói telemetria opcional quando ativa; os eventos essenciais continuam imediatos no ring.

Não se mediu uma percentagem de redução de CPU nem fluidez real nesta máquina. O benefício confirmado é retirar as operações descritas do percurso normal, não um benchmark de dispositivo.

## 4. Validação

**BUILD SUCCESSFUL**, execução completa em 2 min 33 s; nova verificação após fechar os inventários técnicos em 4 s, com resultados de compilação/testes/lint reutilizados porque o código não mudou. Compilação release e debug/consumidores, testes JVM e `lintVitalStandardRelease` aprovados. Relatório lint: `No issues found.`

| Suíte | Testes | Falhas / erros / ignorados |
|---|---:|---:|
| Mobile | 119 | 0 |
| Core privado | 1.193 | 0 |
| player-core | 494 | 0 |
| Total | **1.806** | **0** |

31 testes novos: 30 no player e 1 de ocultação de credenciais no Core. Cobrem substituição/cancelamento de vistas, referência nativa única e libertação de attach parcialmente falhado, concorrência attach/detach, espera válida/limitada/modo áudio, rejeição de origem antiga, libertação terminal uma vez, leituras JNI expiradas e reset do buffer publicado. Os testes de coordenação usam saídas/execução simuladas: não executam MediaCodec nem o JNI mpv Android.

Inventários i18n: Mobile 0 baseline + 585 entradas técnicas; Core 310 baseline + 5.711 técnicas, sem aumento de dívida nem alteração de classificações de outros ficheiros. `git diff --check` aprovado. Mantém-se o aviso geral já existente sobre funcionalidades Gradle descontinuadas para Gradle 10.

Evidência: [MOBILE_111_FASE3_20261023.json](validacao/MOBILE_111_FASE3_20261023.json). Logs finais em `%TEMP%/owntv-mobile-111-phase3-final-validation-20261023.log` e `%TEMP%/owntv-mobile-111-phase3-final-ratchet-20261023.log`.

## 5. Âmbito e rollback

Alterações apenas na Mobile e no seu Core privado. TV e Core externo não foram modificados. Schema privado 52, Media3 1.11.1, libmpv 2026.10.4, volume interno a 100%, HLS estrito/exceções manuais, motor exclusivo, buffer/latência/retoma imediata e implementação HTTP de handover conservados. `TuneOwnership`, `SourceDrain`, `PlaybackHttpCalls`, perfil de probe da fase 2 e edição de listas da fase 1 confirmados por hash face ao snapshot. Base de dados idêntica à cópia verificada anterior.

Snapshot: `C:/Users/renat/AppData/Local/Temp/owntv-mobile-before-phase3-111-20261023`, inventário de 551 entradas, hashes/cópias originais e `scope-verification.json`. O original do teste Core foi recuperado da cópia da fase 2 e confirmado como idêntico ao ficheiro atual sem a única inserção desta fase. As alterações locais anteriores foram preservadas. Não se apagaram dados, fontes ou preferências.

Sem APK, instalação, assinatura, commit ou publicação. A fase 4 continua pendente de autorização própria.

## 6. Aceitação física após gerar o APK

Nenhum dispositivo ADB ligado. Testar num smartphone/tablet com os dois motores:

1. A → B → C → A repetidamente, incluindo regressar a canais já abertos; alternar lista, fullscreen e mini-player sem som residual nem imagem preta persistente.
2. Rodar, carregar em Home e retomar; mudar durante criação/destruição da vista. Confirmar imagem na vista atual e comandos responsivos.
3. Direto e diferido com motor exclusivo; abrir uma falha real e repetir. Erro terminal deve parar o conteúdo; um canal saudável não deve ficar coberto por erro de outra origem.
4. Ativar e desativar diagnóstico e comparar o percurso normal; com diagnóstico ativo verificar attach/detach e amostras mpv sem credenciais.
5. Confirmar volume do sistema, retoma quando há dados reproduzíveis e configurações por canal. Medir tempo da thread principal/CPU em ensaio comparável antes de afirmar ganhos quantitativos.
