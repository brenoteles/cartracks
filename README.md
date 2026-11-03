# CarTracks 🚗

**O Sistema Definitivo de Telemetria, Gestão Veicular e Rastreamento de Trajetos para Automóveis.**

CarTracks é um aplicativo Android nativo construído com **Jetpack Compose**, **MapLibre GL Surface (Hardware-Accelerated)** e **Design System Wonderflow Wanda**. Projetado especialmente para motoristas exigentes que buscam precisão métrica de odômetro, estimativa transparente de combustível/autonomia, suavização de traçado com filtro de Kalman e ajuste inteligente às vias (Map Matching).

---

## 🌟 Principais Recursos

- **🏎️ Painel Dinâmico (Dashboard):**
  - Odômetro manual editável com persistência contínua.
  - Indicador em tempo real de combustível no tanque e autonomia estimada.
  - Acesso rápido a Casa, Trabalho e "Onde Estacionei".
  - Estatísticas de velocidade média, consumo (km/L) e gastos mensais.

- **🗺️ Renderização de Mapas de Alto Desempenho (MapLibre GL Surface):**
  - Motor de renderização OpenGL ES / Vulkan via MapLibre Native SDK (v11.5.2).
  - Camadas em alta definição: **Modo Escuro Wanda**, **Ruas HD** e **Satélite HD**.
  - Traçado colorido por gradiente de velocidade com legenda interativa.

- **🎯 Precisão e Inteligência de Percurso:**
  - **Filtro de Kalman Adaptativo 2D:** Suaviza leituras de GPS e elimina desvios provocados por túneis e prédios altos.
  - **Map Matching Pós-Viagem:** Ajusta geometricamente a trajetória às vias oficiais (algoritmo offline de projeção tangencial com fallback online OSRM).
  - **Pausa Inteligente por Imobilidade:** Pausa automaticamente a gravação após 3 minutos de imobilidade e retoma ao acelerar, economizando bateria e evitando pontos espúrios.

- **⛽ Módulo de Abastecimento e Custos Reais:**
  - Histórico de abastecimentos com cálculo do custo financeiro de cada viagem em Reais (R$).
  - Gerenciamento completo (edição de postos, valores, litros e exclusão).
  - Onboarding de estimativa de combustível por volume e quilômetros rodados.

- **🛡️ Confiabilidade Operacional:**
  - Isenção de otimização de bateria contra encerramento pelo Android.
  - Backup automático em formato JSON com rotação dos últimos 5 arquivos.
  - Importação de backups com deduplicação automática de viagens.

---

## 🛠️ Tecnologias & Arquitetura

- **Linguagem:** Kotlin & Java 17
- **UI Framework:** Jetpack Compose + Material 3 (Design Tokens Wonderflow Wanda)
- **Map Engine:** MapLibre Native SDK 11.5.2 (MapLibre GL Surface)
- **Banco de Dados:** SQLite OpenHelper (Transações ACID locais e seguras)
- **Alvo:** Android 15 / 16 (API Level 35/36)

---

## 📄 Licença

Distribuído sob a licença **Apache License 2.0**.
Copyright © 2026 CarTracks Team & Breno Teles. Todos os direitos reservados.
