(ns motion.generate
  "Pure candidate builder for one co-scientist round (ADR-2607122200 §2/§3).

  Same 'closed hypothesis pool, no LLM in Generation' discipline
  cloud_murakumo.cosci uses: a small enumerable gene pool of
  clip-type/pacing/loop-style variations, persona-flavored via
  `:persona/tags`. round-candidates is a pure function of (persona, round, k)
  — re-running the same round number reproduces the same candidates;
  exploration across the pool happens by round number advancing
  (motion.loop) and by biasing one gene slot toward the previous round's
  elite (motion.cosci/evolve-round)."
  (:require [clojure.string :as str]))

(def gene-pool
  {:clip-type ["idle stance" "casual walk cycle" "brisk run cycle"
               "greeting gesture" "shrug gesture" "point-and-explain gesture"
               "simple two-step dance"]
   :pacing ["slow, relaxed" "normal tempo" "energetic, quick"]
   :loop-style ["seamless loop" "one-shot, returns to idle"]})

(defn- pick [xs seed n] (nth xs (mod (+ seed n) (count xs))))

(defn- gene-for
  "One candidate's gene map. `bias` (from motion.cosci/evolve-round's elite,
  or nil on round 0) pins ONE randomly-chosen slot to the prior winner's
  value instead of round-robining it — elitism without literal crossover
  machinery, honest about being a small closed pool rather than a genuine
  genetic search."
  [round i bias]
  (let [raw {:clip-type  (pick (:clip-type gene-pool) round i)
             :pacing     (pick (:pacing gene-pool) round (+ i 1))
             :loop-style (pick (:loop-style gene-pool) round (+ i 2))}]
    (if (and bias (pos? round) (zero? (mod (+ round i) 3)))
      (merge raw (select-keys bias [(nth [:clip-type :pacing :loop-style] (mod round 3))]))
      raw)))

(defn round-candidates
  "persona + round n (0-based) + k candidates + optional elite bias
  -> [{:candidate/id :prompt :gene :params} ...]."
  ([persona n k] (round-candidates persona n k nil))
  ([{:keys [tags]} n k bias]
   (vec
    (for [i (range k)]
      (let [{:keys [clip-type pacing loop-style]} (gene-for n i bias)]
        {:candidate/id (str "r" n "-c" i)
         :prompt (str/join ", " (concat [clip-type (str pacing " pacing") loop-style]
                                         tags))
         :gene {:clip-type clip-type :pacing pacing :loop-style loop-style}
         :params {}})))))
