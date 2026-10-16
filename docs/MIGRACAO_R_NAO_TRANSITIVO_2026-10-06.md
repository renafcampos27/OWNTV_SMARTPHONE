# Classes R não transitivas — OwnTV Mobile

Data: 06/10/2026. Implementação concluída.

## Alterações

- `gradle.properties`: `android.nonTransitiveRClass=true`.
- `vendor/OwnTV_Core/gradle.properties`: mesma opção explícita no snapshot independente.
- 101 ficheiros Kotlin migrados, com 2 412 referências analisadas/redirecionadas para o módulo proprietário.
- Quando o ficheiro usa apenas recursos do Core, importa `tv.own.owntv.core.R`. Quando mistura recursos locais e partilhados, usa `core.R as CoreR` e mantém `mobile.R` para os locais.
- Fontes, ícones, estilos e strings definidos/substituídos na app continuam a usar `mobile.R`. Nenhum recurso foi eliminado ou copiado para contornar a migração.
- O teste de cobertura da pesquisa reconhece `R` e `CoreR`, mantendo as mesmas verificações e mínimos de cobertura.

## Validação

`BUILD SUCCESSFUL` nas tarefas:

- `:app:compileStandardDebugKotlin`;
- `:app:testStandardDebugUnitTest`;
- `:app:processStandardReleaseResources`;
- `:app:validateSigningStandardRelease`;
- `:OwnTV_Core:core:testDebugUnitTest`;
- `:OwnTV_Core:player-core:testDebugUnitTest`.

Resultados dos testes:

{
  "app": {
    "tests": 48,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "core": {
    "tests": 1049,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "player": {
    "tests": 417,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  }
}

A lista de símbolos compilada da app contém os 78 recursos locais, confirmando que a classe R da app não agrega os recursos das dependências. Não foram medidos tempos antes/depois sob condições comparáveis; a melhoria esperada é na compilação incremental e na quantidade de dependências de R. O valor <0,1 s do aviso original não prova que esta mudança acelere significativamente todo o build.

Não foi gerado APK nem executado teste físico. A assinatura release padrão escolhida pelo utilizador foi preservada. A alteração não pretende reduzir CPU durante reprodução.

## Isolamento e reversão

Alterações deste trabalho apenas no mobile e no respetivo snapshot Core. Não foram editados nem restaurados ficheiros na OwnTV-main ou no Core partilhado. A comparação com a baseline protegida de 04/10 já apresenta outras alterações posteriores e não foi usada para atribuir integridade a esta migração. Os ficheiros mobile/snapshot alterados são independentes, sem hardlinks nas amostras verificadas.

Backup dos ficheiros originais desta migração:
`C:\Users\renat\Downloads\OwnTV_Mobile-main\OwnTV_Mobile-nontransitive-before-20261006.zip`.

No Android Studio, sincronizar os ficheiros Gradle e compilar normalmente. A primeira compilação após migração pode recompilar código devido à alteração da configuração; comparar ganhos em compilações incrementais posteriores.

Documentação: https://developer.android.com/build/optimize-your-build#use-non-transitive-r-classes
