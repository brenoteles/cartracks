# CarTracks 🚗

Aplicativo Android de telemetria e gestão do carro: grava viagens pelo GPS, calcula consumo e custos, mostra rotas frequentes, trânsito histórico e replay de cada viagem. Interface em Jetpack Compose com design Cyberpunk e mapas MapLibre.

## Recursos

- **Viagens**: gravação em primeiro plano, encerramento automático ao estacionar, Bluetooth do carro (iniciar com um toque, encerrar ao desligar), widget e botão nas configurações rápidas.
- **Detalhe da viagem**: traçado colorido por velocidade com setas de sentido, replay com linha do tempo, ficha ao tocar no traçado, perfil de velocidade/altitude, ajuste às ruas (map matching) e exportação GPX/KML.
- **Mapa de rotas**: frequência dos trechos, viagens individuais e trânsito histórico por faixa horária; rotas frequentes com mediana, P90 e melhor horário de saída.
- **Consumo e custos**: km/L por combustível (manual ou medido na bomba, ajustado pelo trânsito), abastecimentos, outras despesas ligadas à manutenção, relatório em imagem.
- **Locais**: Casa, Trabalho e favoritos ilimitados, que nomeiam e filtram as viagens.
- **Dados**: backup automático do Android, cópia JSON no Google Drive, lembretes de manutenção por km.

## Build

```bash
# JAVA_HOME = JDK do Android Studio (jbr)
./gradlew assembleIrreproducibleDebug -PcarTracksVersion=v1.0.x
./gradlew testIrreproducibleDebugUnitTest
```

O APK é gerado só para `arm64-v8a` (ver `ndk.abiFilters` no `build.gradle`; inclua `x86_64` para rodar em emulador).

## Origem e licença

CarTracks é derivado do [OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks) (Apache License 2.0). O núcleo de gravação (`services/`, `sensors/`), o banco de dados e o ContentProvider (`data/`) vêm do OpenTracks e mantêm o esquema original, para que os dados já gravados continuem compatíveis. A interface original do OpenTracks foi substituída e removida. Distribuído sob a mesma licença — ver [LICENSE](LICENSE).
