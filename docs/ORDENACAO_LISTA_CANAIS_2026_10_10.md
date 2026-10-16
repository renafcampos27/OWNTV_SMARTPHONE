# OwnTV Mobile — ordenação na lista de canais

Data: 10/10/2026. Âmbito: Mobile e Core privado; sem alteração do projeto TV ou Core externo.

## Utilização

Na lista de canais, tocar em **Ordenar: …** e escolher:

- **Nome**: ordem alfabética, independente das posições manuais.
- **Fornecedor**: ordem original da lista, independente das posições manuais.
- **Personalizado**: posições guardadas para a lista/categoria atual; os canais sem posição guardada seguem a ordem do fornecedor.

No mesmo painel: **Editar ordem personalizada** abre o editor existente com setas e Guardar/Cancelar. **Restaurar ordem do fornecedor** elimina a ordem manual da lista/categoria atual e seleciona Fornecedor. Ordem de Todos e de Diferidos tem contextos próprios. História mantém a ordem cronológica e não tem editor manual.

## Implementação

- `sortLive` existente guarda a escolha, incluindo CUSTOM; já pertence às definições exportadas no backup.
- `content_order` existente guarda posições por perfil/contexto, sem migração de base de dados.
- O Pager observa a ordenação e é recriado quando o modo muda.
- Nome/Fornecedor deixam de dar prioridade a posições manuais antigas. Em Personalizado sem posições, permanece o caminho simples sem join de ordenação.
- Canais ocultos e versões agrupadas não aparecem no editor; posições previamente guardadas fora da edição são preservadas. Editor limitado aos 5 000 canais já previstos pelo projeto.
- A ordenação do ecrã de personalização das definições é local e não substitui a escolha guardada da lista de canais.

## Validação

Validação final concluída: **BUILD SUCCESSFUL**. Compilação Standard Release, 71 testes da app e 1 108 do Core, Lint da release e controlo de internacionalização passaram, sem falhas nos testes.

Quatro testes de fusão de ordem e seis testes de seleção de consultas cobrem posições ocultas, novos canais, separação Nome/Fornecedor/Personalizado e fallback sem posições. Verificação SQLite das consultas reais: ordem, perfis/fontes, contexto de Diferidos e restauro passaram.

Não foi gerado APK nem realizado teste visual/foco em dispositivo. Testar: mudar os três modos, editar/guardar/cancelar, restaurar, fechar/reabrir a app e confirmar que visitar as definições de personalização não altera o modo escolhido.
