# Max Performance — Relatório de Entregas & Changelog de Sprints

Este documento reúne o histórico consolidado de entregas, melhorias de arquitetura, refatorações e relatórios de sprint do **Max Performance**.

---

## 🚀 Mega-Sessão de Engenharia — Rumo ao 9/10 (16/10/2026)

### 1. HOTFIX-CAROUSEL-CURATION · Correção de Causa Raiz no Hero Card & Carrossel
- **Correção do Parser de Treinos:** Identificada e corrigida a falha no parser que forçava indevidamente `"Upper A"` e `"SUPERIOR"` ao recarregar a recomendação.
- **Preservação de IDs Específicos:** Garantida a preservação dos IDs de protocolos (`protocol_fb_a`, `protocol_fb_b`, `protocol_fb_c`, `protocol_fb_d`).
- **Exclusão Mútua de Seleção:** Corrigida a colisão de estado estabelecendo exclusão mútua entre `preferredProtocolId` e `_preferredWorkoutId` no `MainViewModel`.
- **Curadoria Inteligente do Carrossel:** O `HorizontalPager` do Hero Card foi limitado a no máximo 5 a 6 cards relevantes (incluindo os treinos Full Body A/B/C/D estruturados e a Sessão de Corrida por padrão), eliminando redundâncias.

### 2. REFAC-01A + REFAC-01B · Desmonte de God Class no Módulo de Corrida
- **Desbloqueio de Manutenibilidade:** A classe monolítica `RunningModuleScreen.kt` foi reduzida de **4.153 linhas para 2.510 linhas (-39.5%)**.
- **Módulos Extraídos sem Regressões:**
  - `RunningSetupComponents.kt` (572 linhas): Componentes de seleção de alvo (Tempo, Distância, Livre, Pace), seletor de áudio e configurações pré-corrida.
  - `RunningHudAndDialogs.kt` (1.140 linhas): HUD ao vivo com anel de progresso, velocímetro, controles de pausa/recomeço, diálogo de salvamento e modais de áudio/voz.

### 3. REFAC-02A + UX-SCHEDULE · Redesign do Cronograma de 7 Dias
- **Extração Modular:** Seção extraída para `app/src/main/java/com/example/ui/screens/WeeklyScheduleComponents.kt` (357 linhas).
- **Remoção de Card Concorrente:** Eliminado o card grande inferior que exibia `"Cardio HIIT Abs"` e o segundo botão laranja duplicado `"▶ INICIAR TREINO DE HOJE"`.
- **Faixa Compacta de Resumo (`WeeklyScheduleCompactSummary`):**
  - Design elegante de 1 linha em fundo grafite (`#1C1C1E`/`#242429`) e borda sutil.
  - Sincronização em tempo real:
    - **Hoje (`selectedDayOffset == 0`)**: Reflete exatamente o treino ativo no Hero Card (ex: `"Hoje: Full Body B • Corpo Inteiro (No topo ↑)"`).
    - **Dias Futuros**: Projeta a alternância real do plano (Full Body A/B/C/D e Sessões de Corrida / Descanso no Domingo).
    - **Dias Passados**: Exibe treinos concluídos ou status de descanso.
  - **Zero Emojis Nativos:** Substituição total por ícones vetoriais Material (`Icons.Default.*`).

### 4. UX-HERO-PEEK + SPRINT-UX-PEEK-TEXT + SPRINT-UX-PEEK-BADGES · "Espiar Treino" no Hero Card
- **Novo Componente Modular (`HeroWorkoutPreviewSheet.kt`):** Modal BottomSheet ativado pelo ícone de olho (`Icons.Default.Visibility`) direto no Hero Card.
- **Experiência Visual de Alta Precisão:**
  - **Treinos de Musculação:** Fundo Grafite (`#1C1C1E`), cards em `#242429` com destaque Laranja (`#FF6B35`).
  - **Cards de Corrida:** Visão geral da Sessão Aeróbica com destaque Ciano (`#00D4FF`).
- **Nomes Completos Sem Truncamento:** Nomes de exercícios e variações (ex: `"Agachamento Livre (ou Hack Squat...)"`) exibidos em até 2 linhas completas sem reticências.
- **Badges Circulares Numerados:** Números de exercícios (1, 2, 3...) destacados em badges circulares de `26.dp` na cor temática do treino (`#FF6B35` ou `#00D4FF`), garantindo leitura clara e legibilidade impecável.

### 5. SPRINT-SENS-GPS-01 · Sensor Fusion & Filtro Anti-Drift no GPS
- **Rastreamento Nativo de Cadência (`StepCadenceTracker.kt`):** Implementado rastreador leve baseado em `Sensor.TYPE_STEP_DETECTOR` com janela deslizante de passos para SPM (passos por minuto) em tempo real e indicador de movimento físico real.
- **4 Travas de Qualidade Comercial no GPS (`GpsPointRecorder.kt` / `GpsEvaluator.kt`):**
  1. *Accuracy Gate:* Ignora pontos de satélite com incerteza horizontal > 22 metros.
  2. *Max Human Speed Gate:* Filtra velocidades instantâneas acima de 8.5 m/s (~30.6 km/h) para eliminar saltos irracionais.
  3. *Zero-Cadence Stationary Lock:* Bloqueia acúmulo de distância por oscilação do GPS (*GPS Drift*) quando o usuário está parado (cadência = 0 SPM).
  4. *Minimum Displacement Gate:* Exige deslocamento mínimo válido antes de somar distância ao treino.

---

## 📜 Histórico Consolidado de SprintsAnteriores

### Sprint UX-010 & Ajustes de Precisão de Carga
- **Inferência Inteligente de Equipamentos (`WeightCalculator.inferEquipment`):** Categorização dinâmica entre Barra, Halteres, Polia, Máquina, Smith e Peso Corporal.
- **Clareza de Peso por Equipamento:**
  - Halteres: Rótulo *"POR HALTER"* e indicação *"Carga por halter (1 em cada mão)"*.
  - Barra: Rótulo *"TOTAL KG"* e anotação *"X kg por lado (+ 20kg barra)"*.
  - Exercícios Unilaterais: Sinalização visual Neon *"CARGA (cada lado)"*.
- **Metadados de Exercícios (`Models.kt`):** Inclusão de `isUnilateral` e `weightIncrement` específicos por exercício.
- **Cadastro Rápido de Aparelhos:** Formulário inline no diálogo de novos exercícios para adição instantânea de aparelhos personalizados.

### Hotfix UX-001 & Reestruturação da Tela Inicial (Home)
- **Remoção de Listas Estáticas Longas:** Substituição da lista estática vertical na Home por aba dedicada.
- **CTA Inteligente e Dinâmico:** Botão de início rápido na Home adaptável ao estado (retomar treino ativo ou iniciar treino sugerido).
- **Carrossel Horizontal de Treinos (`HorizontalPager`):** Apresentação visual fluida dos treinos A, B, C, D, E com badges de progresso e estimativas.
- **Resumo Semanal Ultracompacto:** Card horizontal de 1 linha com métricas de Treinos Realizados, Tempo Total e Ofensiva (Streak).

### Estabilidade de Áudio & Ciclo de Vida
- **Debounce de Som de Término de Descanso (`MaxSoundManager.kt`):** Prevenção de reprodução duplicada de áudio no fim do timer de descanso.
- **Persistência Reativa e Reidratação:** Salvamento e restauração automática do progresso no `MainViewModel` sem flickering.
- **Tratamento do Botão Voltar:** Interceptador `BackHandler` mantendo treino ativo em background sem perda de estado.
