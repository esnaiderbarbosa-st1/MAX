# Especificação de Arquitetura Mestra: Workout Composition Engine (WCE) & Training Experience Layer (TEL)

**Documento Ref:** ARCH-WCE-TEL-MASTER  
**Status:** APROVADO & EMBUTIDO NO CÓDIGO  
**Autor:** Principal Software Architect, Staff Strength Coach, Recommendation Systems Lead  

---

## 1. Introdução e Contexto Arquitetural

Com o advento da **Training Experience Layer (TEL)**, do **Exercise Knowledge Graph (EKG)** e do **Workout Composition Engine (WCE)**, o pipeline de recomendação do projeto **Max Performance** tornou-se altamente modular, resiliente e centrado na experiência do praticante.

Este documento unifica a arquitetura da camada de treino, preenchendo a lacuna entre o cálculo de variáveis puramente fisiológicas (volume total de séries, intensidade e progresso) e o refinamento estético, espacial e biomecânico da experiência do usuário.

### O Pipeline de Execução Atualizado:

```
+-----------------------------------+
|       1. DECISION ENGINE          | -> Avalia prontidão biológica, fadiga e travas de segurança
+-----------------+-----------------+
                  |
                  v
+-----------------------------------+
|   2. CORE RECOMMENDATION ENGINE   | -> Calcula volumes alvos (séries), repetições e intensidade (RPE)
+-----------------+-----------------+
                  | [Metas Fisiológicas Cruas]
                  v
+-----------------------------------+
| 3. WORKOUT COMPOSITION ENGINE(WCE)| -> Determina quantidade de exercícios, vetores biomecânicos,
+-----------------+-----------------+    distribuição e fluxo neuromuscular
                  |
                  v
+-----------------------------------+
|   4. TRAINING EXP. LAYER (TEL)    | -> Resolve alinhamento de ambiente/equipamentos,
+-----------------+-----------------+    Perceived Completeness Score (PCS) e curadoria visual
                  |
                  v
+-----------------------------------+
|    5. WORKOUT BUILDER / PAYLOAD   | -> Hidrata os exercícios reais da biblioteca local Room
+-----------------------------------+    e gera o Payload de Sessão para o Player
```

---

## 2. Regras Principais de Engenharia de Treino

1. **A Fisiologia é Inviolável (Primazia do Bio-Engine):**
   - A TEL/WCE pode ajustar os implementos (haltere vs. cabo), reordenar as sequências ou consolidar exercícios, mas **nunca reduz o volume total de séries alvo** nem dilui o RPE estipulado pelo Recommendation Engine.
2. **Contexto Sobrepõe a Estética (Primazia do Mundo Real):**
   - Restrições de tempo, equipamentos disponíveis e limitações articulares definidas pelo Decision Engine são travas rígidas.
3. **Continuidade de Progressão Coesa:**
   - O sequenciamento prioriza demanda neurológica primeiro (compostos multiarticulares pesados), densidade metabólica segundo (isoladores/máquinas) e finalizadores (finishers) por último.
4. **Governança Declarativa Zero-Code:**
   - As regras e perfis são definidos via modelos imutáveis e gerenciáveis.

---

## 3. Heurísticas do Workout Composition Engine (WCE)

### 3.1. Quantidade Adaptativa de Exercícios ($N_e$)
Para evitar extremos (treinos longos e monótonos ou treinos truncados), o WCE calcula dinamicamente a quantidade de exercícios ($N_e$) com base no volume alvo de séries ($S_{total}$) e no tempo disponível ($T_{min}$):

$$N_e = \min\left( \max\left( \left\lfloor \frac{S_{total}}{3.2} \right\rfloor, 3 \right), \left\lfloor \frac{T_{min}}{12} \right\rfloor \right)$$

### 3.2. Distribuição Dinâmica de Séries
O WCE aloca as séries entre os slots de exercício usando perfis estruturados:
- **Modelo Pirâmide Decrescente (Força/Hipertrofia):** $5 + 4 + 3$ ou $4 + 3 + 3$. Aloca maior volume no movimento composto inicial.
- **Modelo Equitativo Concentrado (Iniciantes/Intermediários):** $3 + 3 + 3$. Mantém volume constante para consolidação de padrão motor.
- **Modelo Carga de Pico Invertida:** $3 + 4 + 3$. Dá foco a um exercício específico no meio do treino.

### 3.3. Cobertura Biomecânica e Vetores de Força
Garante que os slots de treino cubram diferentes vetores biomecânicos e perfis de resistência (ex: Press Horizontal + Press Inclinado + Fly Horizontal), evitando treinos com exercícios redundantes.

### 3.4. Diversidade de Implementos
Quando $N_e \ge 3$, não mais que **50%** dos exercícios compartilham o mesmo tipo de implemento (ex: apenas barra livre). O treino mescla Peso Livre, Cabos e Máquinas.

### 3.5. Fluxo Neuromuscular (IDN)
Sequencia os exercícios do maior para o menor Índice de Demanda Neuromuscular (IDN):  
*Compostos Livres → Compostos em Máquinas → Isoladores → Finalizadores Metabólicos.*

---

## 4. Heurísticas da Training Experience Layer (TEL) & Perceived Completeness Score (PCS)

A TEL calcula o **Perceived Completeness Score (PCS)**, uma métrica de experiência preditiva que avalia a satisfação percebida pelo praticante ao concluir a sessão:

$$\text{PCS} = (0.3 \times S_{\text{sequencing}}) + (0.3 \times S_{\text{symmetry}}) + (0.2 \times S_{\text{context}}) + (0.2 \times S_{\text{pump}})$$

Onde:
1. **$S_{\text{sequencing}}$ (Pacing):** Começa com movimentos multiarticulares exigentes e encerra com isolamento.
2. **$S_{\text{symmetry}}$ (Balanço Antagonista):** Garante equilíbrio entre movimentos de empurrar/puxar ou agrupamentos complementares.
3. **$S_{\text{context}}$ (Alinhamento de Ambiente):** Prioriza máquinas de alta estabilidade na academia comercial ou halteres/peso corporal em casa.
4. **$S_{\text{pump}}$ (Estímulo Metabólico):** Garante que o último exercício promova acúmulo metabólico (faixa de repetições maior e tensão constante).

*Regra:* O esqueleto do treino deve atingir $\text{PCS} \ge 85/100$. Caso contrário, a TEL executa um loop de otimização para substituir implementos ou reordenar slots.

---

## 5. Árvore de Resolução de Conflitos e Matriz de Prioridade

Quando diretrizes fisiológicas e heurísticas de experiência entram em conflito, a seguinte árvore de decisão governa o sistema:

```
[ CONFLITO DETECTADO ]
        |
        +---> Prioridade 1: Segurança Clínica & Biológica (Decision Engine) [INVIOLÁVEL]
        |
        +---> Prioridade 2: Volume Total de Séries e Intensidade RPE (Rec Engine) [PRESERVADO]
        |
        +---> Prioridade 3: Adequação de Equipamentos ao Ambiente (TEL) [AJUSTÁVEL]
        |
        +---> Prioridade 4: Variedade de Implementos e Sequência (TEL/WCE) [FLEXÍVEL]
```

### Exemplos de Resolução:
- **Restrição Articular x Variedade de Agachamento:** Se o Decision Engine proibir carga axial na coluna, o agachamento livre é substituído por Leg Press ou agachamento em cinto, mantendo o volume e a meta muscular.
- **Limite de Tempo Severo x Alto Volume:** Se o praticante possui apenas 30 minutos e precisa de 16 séries, as séries são agrupadas em cluster-sets / rest-pause mantendo o volume total na mesma janela de tempo.

---

## 6. Algoritmo Integrado Pseudo-Code

```python
def process_workout_composition_and_experience(physiological_targets, user_context, gym_profile):
    """
    Inputs:
      - physiological_targets: { target_muscles: List, total_sets: Int, target_rpe: Int }
      - user_context: { time_available: Int, split_name: String }
      - gym_profile: { is_commercial_gym: Boolean, available_implements: List }
    Output:
      - final_session_skeleton: List[ExerciseSlot]
    """
    # 1. WCE: Calcula quantidade adaptativa de exercícios
    N_e = calculate_adaptive_exercise_count(user_context.time_available, physiological_targets.total_sets)
    
    # 2. WCE: Distribuição de séries e vetores biomecânicos
    sets_dist = distribute_sets_to_slots(physiological_targets.total_sets, N_e)
    vectors = resolve_biomechanical_vectors(physiological_targets.target_muscles, N_e)
    
    # 3. TEL: Filtragem de implementos com base no ambiente (Academia vs. Casa)
    implements = filter_implements_for_environment(N_e, gym_profile)
    
    # 4. WCE/TEL: Sequenciamento por Demanda Neuromuscular (IDN)
    slots = create_and_sequence_slots(sets_dist, vectors, implements)
    
    # 5. TEL: Avaliação e otimização do Perceived Completeness Score (PCS)
    pcs = calculate_pcs(slots, gym_profile)
    if pcs < 85:
        slots = optimize_slots_for_pcs(slots, gym_profile)
        
    return slots
```
