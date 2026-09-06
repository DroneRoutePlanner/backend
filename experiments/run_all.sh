#!/bin/zsh
# Pełny protokół eksperymentalny (odtwarzalny: stałe ziarna). Czas ~10–15 min.
set -e
cd "$(dirname "$0")/.."
CP=build/classes/java/main
MAIN=org.example.bench.AlgorithmComparison
java -cp $CP $MAIN pop=100 iter=500  runs=20 seed=42 checkpoints=50 out=experiments/results/main
java -cp $CP $MAIN pop=100 iter=100  runs=10 seed=42 checkpoints=50 out=experiments/results/budget100
java -cp $CP $MAIN pop=100 iter=250  runs=10 seed=42 checkpoints=50 out=experiments/results/budget250
java -cp $CP $MAIN pop=100 iter=1000 runs=10 seed=42 checkpoints=50 out=experiments/results/budget1000

java -cp $CP $MAIN pop=100 iter=2000 runs=10 seed=42 checkpoints=50 out=experiments/results/budget2000
java -cp $CP $MAIN pop=100 iter=5000 runs=10 seed=42 checkpoints=50 out=experiments/results/budget5000
echo ALL_DONE
