# 🚗 CarTracks - Open-Source Automotive GPS Tracker

**CarTracks** é um aplicativo Android open-source dedicado exclusivamente ao rastreamento, telemetria e gestão de viagens de carro.

> **Fork mantido por [Breno Teles](https://github.com/brenoteles)** — baseado originalmente no OpenTracks e transformado em um car tracker completo com interface automotiva de alto contraste e telemetria veicular.

---

## ⚡ Principais Funcionalidades

- **🗺️ Suporte Nativo ao OpenStreetMap:**
  - Sem necessidade de chaves de API pagas ou aplicativos externos complementares.
  - Exibição de trajetos com gradiente de velocidade por cores:
    - 🟢 **Verde (< 40 km/h):** Tráfego Urbano / Semáforo
    - 🟡 **Âmbar (40 - 80 km/h):** Velocidade Moderada / Fluida
    - 🔴 **Vermelho (> 80 km/h):** Alta Velocidade / Rodovia
  - Marcador de destaque automático para o ponto de pico de velocidade (⚡ maior velocidade da viagem).

- **🚘 Painel da Garagem:**
  - Quilometragem acumulada, combustível estimado consumido e custo em R$.
  - Consumo médio em km/L e identificação de destinos e postos de combustível mais frequentes.

- **⛽ Gestão de Abastecimento:**
  - Registro de abastecimentos com cálculo automático de custo total, odômetro, posto e tipo de combustível.
  - Histórico detalhado integrado na aba dedicada.

- **🔌 Automação Veicular:**
  - Início e parada automática de rastreamento ao conectar/desconectar do carregador do veículo ou do som Bluetooth do carro.

- **🎨 Interface Moderna:**
  - Interface desenvolvida em Framework7 com tema escuro de alto contraste, ideal para visualização em suportes veiculares dia e noite.

---

## 📦 Download do APK

Você pode baixar a versão compilada diretamente na seção de [Releases](https://github.com/brenoteles/cartracks/releases) do repositório:
- `CarTracks.apk`

---

## 🛠️ Compilação

Para compilar o projeto localmente:

```bash
git clone https://github.com/brenoteles/cartracks.git
cd cartracks
./gradlew assembleIrreproducibleDebug
```

O APK compilado estará em:
`build/outputs/apk/irreproducible/debug/CarTracks__6766-irreproducible-debug.apk`

---

## 📄 Licença

Distribuído sob a licença [Apache 2.0](LICENSE).
