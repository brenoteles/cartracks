# CarTracks — handoff para a próxima sessão

Leia isto primeiro. Estado em 2026-11-07, após a v1.0.27 (resumo semanal, recordes, insights, retrospectiva, Ajustes novos; inclui a correção dos widgets da v1.0.26), publicada.

## 1. O que é

App Android (Kotlin/Java, Jetpack Compose, MapLibre 11.13) que grava viagens de carro e mostra consumo, custos, rotas, trânsito histórico e replay. Fork do OpenTracks (Apache-2.0): o núcleo de gravação, o banco e o ContentProvider vêm de lá; toda a interface é nova (Compose, design Cyberpunk).

- Repositório: `C:\Users\death\.gemini\antigravity\scratch\car-tracks` → GitHub `brenoteles/cartracks` (branch `main`).
- Usuário: dono do app, usa todo dia num Galaxy S24 Ultra (Android 16), conectado por ADB Wi-Fi. Fala português; respostas em português do Brasil.
- Relatório de versões para o usuário: `C:\Users\death\Desktop\CarTracks_Relatorio.html` — **atualizar a cada versão** (nova seção no topo, renumerar as `<h2>`, linha na tabela "Linha do Tempo", badges do topo).

## 2. Regras que não podem ser quebradas (dados instalados)

- `applicationId` `de.dennisguse.cartracks` (+ `.debug` no build usado) e assinatura com `~/.android/debug.keystore` (SHA-256 começa `d4289667…bcc3d`). Conferir com `apksigner verify --print-certs` antes de instalar.
- `versionCode` só sobe (está em **6777** na v1.0.27, em `build.gradle` → `defaultConfig`).
- Não mudar esquema do banco (`CustomSQLiteOpenHelper`, versão 42), colunas de `data/tables/*`, nem chaves de SharedPreferences sem migração. Arquivos de prefs: `CarTracksPrefs`, `CarTracksParking`, `CarTracksLocations` (Casa/Trabalho/`favorites_json`) e o padrão `<pacote>_preferences`.
- Os modelos `HeartRate`, `Cadence`, `Power`, `Temperature` mapeiam colunas que existem no banco: **não apagar** mesmo que os sensores saiam.

## 3. Build, teste e entrega

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleIrreproducibleDebug -PcarTracksVersion=v1.0.x     # APK em build/outputs/apk/irreproducible/debug/
./gradlew testIrreproducibleDebugUnitTest                           # 41 testes JVM
./gradlew compileIrreproducibleDebugAndroidTestJavaWithJavac        # testes instrumentados só compilam (não há emulador)
ADB=/c/Users/death/AppData/Local/Android/Sdk/platform-tools/adb.exe
$ADB install -r <apk>                                               # mantém os dados
```

- APK só `arm64-v8a` (`ndk.abiFilters`); adicionar `x86_64` para emulador.
- **R8 ligado no build `debug`** (desde a v1.0.24): `minifyEnabled` + `shrinkResources`, regras em `proguard-rules.pro` com `-dontobfuscate` (nomes de enums gravados no banco/prefs/backup, como `ExpenseCategory`, `MaintenanceStatus` e `FuelFamily`, precisam continuar iguais) e MapLibre mantido inteiro por causa do JNI. Não adicionar `-keep androidx.compose.**`, porque traria de volta os ícones (~50 MB de dex). Classe nova acessada só por reflexão ou JNI precisa de regra.
- **Para medir o tamanho, rodar `./gradlew clean` antes**: o empacotamento incremental do debug deixa espaço vazio no APK (o arquivo chegou a 39 MB com 21 MB de conteúdo).
- Copiar o APK para `C:\Users\death\Desktop\CarTracks_v1.0.x.apk`.
- Testar no aparelho: `adb shell input tap` + `adb exec-out screencap -p` (tela 1440×3120; abas no rodapé em y≈2930: Início x≈199, Viagens 544, Custos 870, Ajustes 1246). `uiautomator dump /dev/tty` (com `MSYS_NO_PATHCONV=1`) acha coordenadas por texto.
- **Publicar só quando o usuário pedir** ("publique"). Fluxo: commit → `git tag -a vX` → `git push origin main` + tag → `gh release create vX <apk> --notes-file <md UTF-8>` (o `gh` está logado; atalho em `~/bin/gh`). Notas sempre em arquivo UTF-8 (acentos já quebraram na v1.0.17).
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## 4. Estado do git

- Tudo publicado até **v1.0.27** (tags e releases no GitHub com o APK; v1.0.27 é a "Latest", 22,5 MB). A v1.0.26 não tem tag própria: foi junto na v1.0.27.
- `Instrucoes Claude.txt` na raiz é do usuário: nunca commitar.

## 5. Mapa do código (`src/main/java/de/dennisguse/opentracks/`)

| Área | Arquivos |
|---|---|
| Entrada / navegação | `CarMainActivity.kt` (estado de navegação, carga de viagens/pontos, liga telas e serviço), `Startup.java` (Application) |
| Telas Compose | `ui/screens/`: Dashboard, Trips, TripDetail (replay, ficha, perfil), PeriodMap (Frequência/Viagens/Trânsito), Report, Fuel, Costs, Maintenance, Settings, LiveCockpit |
| Componentes | `ui/components/CarMapView.kt` (MapLibre: polylines, setas de sentido, carro como camada que gira e desliza, mapa de frequência, segmentos coloridos, toque no traçado), `LocationPickerMapDialog`, `ConsumptionSettings`, `VehicleSetupDialog`, `AutoWidgets`, `EditTripDialog` |
| Tema | `ui/theme/CarTracksTheme.kt` (cores `Cyber*`, `cyberPanel()`, `EyebrowStyle`, `displayStyle()`, `monoStyle()`) |
| Análises (Kotlin puro, testadas) | `util/RouteAnalysis.kt` (frequência, agrupamento de rotas), `TrafficAnalysis.kt` (trânsito por horário, escrito pelo Gemini), `TripReplay.kt`, `FuelConsumptionModel.kt`, `FavoritePlaces.kt`, `GpsKalmanFilter.kt`, `MapMatchingService.kt` (OSRM) |
| Carro / avisos | `util/CarBluetooth.kt`, `services/AutoTrackingReceiver.java` (Bluetooth do carro), `util/CarNotifications.kt`, `services/QuickControls.kt` (widget + tile), `services/ParkedDetector.java` |
| Gravação (OpenTracks) | `services/TrackRecordingService.java` (+ `endTripAndPark`), `TrackRecordingManager`, `handlers/TrackPointCreator`, `sensors/` |
| Dados | `data/` (ContentProvider, SQLite, iterators), `FuelDatabaseHelper`, `ExpenseDatabaseHelper.kt`, `util/MaintenanceStore.kt`, `io/CarTracksBackupHelper.kt` (JSON), `io/TrackExportHelper.kt` (GPX/KML do app) |

Testes JVM em `src/test/java` (RouteAnalysis, HeatBins, FavoritePlaces, TripReplay, TrafficAnalysis, consumo…).

## 6. Fase 2 da limpeza — concluída na v1.0.24

Feito, um item por commit, com gravação de teste no aparelho: anúncios de voz, sensores Bluetooth de esporte (a gravação usa só GPS + barômetro interno; colunas de sensor gravam `null`), layout de gravação customizável (Gemini), `PreferencesUtils` de 823 para 370 linhas sem `androidx.preference` (mesmo arquivo `<pacote>_preferences`), 157 recursos sem uso, e R8. APK de 38,7 para 21,9 MB.

O que ainda dá para fazer, se o usuário quiser:
- Renomear o pacote `de.dennisguse.opentracks` **sem** mudar o `applicationId` nem a authority do ContentProvider.
- `ShareContentProvider`, `TrackFileFormat` com KMZ/CSV e restos de `publicapi/` ainda existem; conferir o uso com `jdeps` antes de tirar.

Ferramentas usadas na limpeza: grafo de classes com `jdeps -verbose:class`; `./gradlew lintIrreproducibleDebug` (`UnusedResources`; o lint grava estilos com `_` no lugar de `.`); remoção de recurso validada comparando os nomes antes/depois com a lista do lint.

## 6b. v1.0.25/26: o que entrou e como funciona

- **Abastecimento**: `ui/components/FuelEntrySheet.kt` (tela cheia) + `util/FuelAmounts.kt` (dois campos digitados dão o terceiro; testado em `FuelAmountsTest`). O botão do Início abre o formulário direto (`openAddForm` em `FuelScreen`).
- **Widgets** (`services/CarWidgets.kt` + `QuickControls.kt`): `RecordWidget` (Painel; manteve o nome da classe para não sumir da tela inicial), `LastTripWidget`, `MonthCostWidget`. Eles só leem um `WidgetSnapshot` em JSON no prefs `CarTracksWidgets`, publicado por `CarMainActivity.publishWidgetSnapshot` quando viagens, abastecimentos, despesas ou o tanque mudam. Limitação: viagem encerrada com o app fechado (Bluetooth/estacionar) só atualiza "Última viagem" na próxima abertura do app. Toques: `CarWidgets.ACTION_OPEN_TRIP`/`ACTION_OPEN_COSTS` tratados em `handleQuickAction` → `widgetNavState`.
- Removido o cartão "Melhor horário de saída" (o usuário usa o Waze). A bússola do mapa fica no canto superior esquerdo.
- **Pendente de resposta do usuário**: início 100% automático pelo Bluetooth do carro exige `ACCESS_BACKGROUND_LOCATION` ("Permitir o tempo todo"); o app já tem isenção de bateria.
- **v1.0.26 (regras de layout de widget, aprendidas no S24)**: nada de `<View>` puro (o launcher recusa: "Class not allowed to be inflated android.view.View"; usar `FrameLayout`), e `@font/...` não é aplicado na tela inicial, então os widgets usam `sans-serif-condensed`/`sans-serif-medium`. Erros de widget aparecem no logcat com a tag `AppWidgetHostView`. Toque de widget fecha viagem/mapa/relatório abertos antes de trocar de aba.
- Estudo de Waze e Samsung Health (REA + aapt2 + Gemini) com 12 sugestões priorizadas: seção 3 do relatório no Desktop. No REA, os decodificadores Android (apktool/JADX) não estão configurados e o adb precisa de `REA_ADB_PATH`, então os APKs foram puxados com o adb do SDK.

## 6c. v1.0.27: análises da semana e do ano

- **Dados fora da Activity**: `data/TripLoader.kt` carrega viagens (com ponto de partida/chegada novos em `TripItem`), abastecimentos, despesas e o modelo de consumo só com um Context; também `tankLiters`. A Activity, o widget e o resumo semanal usam ele.
- **Análises puras e testadas**: `util/InsightModels.kt` (contratos), `util/DrivingInsights.kt` (semana móvel de 7 dias, recordes, insights, janela de saída, retrospectiva; escrito pelo Gemini 4 Argon), `util/RouteVariants.kt` (caminhos por células de 200 m e Jaccard ≥ 0,55; nome da via pelo Geocoder no ponto exclusivo de cada caminho).
- **Início**: `ui/screens/InsightsSection.kt` + `ui/components/InsightCards.kt`. 👍/👎 e recordes anteriores em `util/InsightStore.kt` (prefs `CarTracksInsights`); selo por 7 dias.
- **Resumo semanal**: `services/WeeklySummary.kt` (alarme inexato domingo 20h, receiver também em BOOT/MY_PACKAGE_REPLACED; pref `weekly_summary_enabled`). Toque/widget → `ACTION_OPEN_WEEK` → Relatório com `startOnLastWeek`.
- **Lembrete ao desligar**: `util/CarOffReminder.kt` (prefs `car_off_reminder_enabled`/`car_off_reminder_items`), postado por `AutoTrackingReceiver` no ACL_DISCONNECTED do carro; `endTripAndPark(null)` não posta o aviso antigo. Não testado de ponta a ponta: o usuário ainda não escolheu o Bluetooth do carro.
- **Retrospectiva**: `ui/screens/YearRecapScreen.kt` (Gemini 4 Argon), cidades via Geocoder em `CarMainActivity.buildYearRecap`. O cartão de entrada só aparece em dezembro (ano corrente) e janeiro (ano anterior): `recapYear()` em `InsightsSection.kt`.
- **Ajustes**: `SettingsScreen.kt` redesenhado pelo Gemini 3.8 Flash; mesmas chaves de prefs.
- Widget `MonthCostWidget` agora mostra os últimos 7 dias (nome da classe mantido).
- Depois da ultrareview: `loadTrips` roda em IO, então `tripsLoadedState` segura o `notifyDue` de manutenção e a publicação dos widgets até a primeira carga; o widget semanal se refaz a cada 3 h (`updatePeriodMillis`) e após atualizar o app; todo auto-stop (`endTripAndPark`) atualiza os widgets; selo de recorde só quando o recorde vem de dado mais novo (`atMs` maior); o primeiro mês (parcial) não conta para "mês mais barato"; insights ocultos voltam por um botão em Ajustes → Notificações.

## 7. Ideias pendentes (já discutidas com o usuário)

- Traçado colorido por altitude (alternativa ao de velocidade).
- Seguir o carro no mapa ao vivo ainda redesenha as polylines a cada novo ponto (`points` muda) — mover o traçado ao vivo para uma fonte GeoJSON atualizada incrementalmente.
- Trânsito histórico: hoje desenha por viagem com sobreposição; opção `deduplicate` existe em `TrafficAnalysis`.

## 8. Trabalhando com o Gemini 3.8 Flash (o usuário pede isso)

CLI do Antigravity: `agy` (`C:\Users\death\AppData\Local\agy\bin\agy`). Modelo `"Gemini 3.8 Flash (High)"`.

- Revisão/auditoria somente leitura: `agy -p "<prompt>" --model "Gemini 3.8 Flash (High)" --mode plan > saida.txt` (em segundo plano, 5–15 min).
- Com edição, sem mexer na configuração global (que nega `write_file(*)` e `command(*)` — não alterar, protege outros projetos):
  `--mode accept-edits --settings '{"permissions":{"allow":["write_file(<caminho absoluto de cada arquivo dele>)"],"deny":["command(*)"]}}'`
- Dar ao Gemini uma lista exclusiva de arquivos, compilar/testar por ele, revisar o que ele escreveu e creditar no commit e no relatório.

## 9. Memória do Claude

`C:\Users\death\.claude\projects\C--Users-death--gemini-antigravity-scratch-car-tracks\memory\` (`cartracks-release-workflow.md`, `gemini-collaboration.md`).
