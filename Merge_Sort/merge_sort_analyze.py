import pandas as pd
import matplotlib.pyplot as plt

# ------------------------------------------------------------
# 1. CSV laden
# ------------------------------------------------------------
df = pd.read_csv("results.csv")

# Score bereinigen
df["Score"] = (
    df["Score"]
    .astype(str)
    .str.replace(",", ".")
    .str.extract(r"([0-9.]+)")
    .astype(float)
)

print("Spalten:")
print(df.columns)
print(df.head())

# ------------------------------------------------------------
# 2. Param-Namen anpassen
# ------------------------------------------------------------
df = df.rename(columns={
    "Param: size": "size",
    "Param: parallelism": "parallelism",
    "Param: cutoff": "cutoff"
})

# Falls sequential keinen parallelism/cutoff besitzt:
if "parallelism" not in df.columns:
    df["parallelism"] = 1

if "cutoff" not in df.columns:
    df["cutoff"] = 0

# ------------------------------------------------------------
# 3. Benchmarks trennen
# ------------------------------------------------------------
seq = df[df["Benchmark"].str.contains("sequential")].copy()
par = df[df["Benchmark"].str.contains("parallel")].copy()

# ------------------------------------------------------------
# 4. Mittelwerte bilden
# ------------------------------------------------------------
seq_mean = seq.groupby("size")["Score"].mean()

par_mean = (
    par.groupby(["size", "parallelism", "cutoff"])["Score"]
    .mean()
    .reset_index()
)


# ------------------------------------------------------------
# 5. Speedup berechnen
# ------------------------------------------------------------
par_mean["seq_time"] = par_mean["size"].map(seq_mean)

# Speedup = T_seq / T_par
par_mean["speedup"] = (
    par_mean["seq_time"] / par_mean["Score"]
)

# Effizienz = Speedup / p
par_mean["efficiency"] = (
    par_mean["speedup"] / par_mean["parallelism"]
)

# Overhead = T_par - (T_seq / p)
par_mean["ideal_parallel_time"] = (
    par_mean["seq_time"] / par_mean["parallelism"]
)

par_mean["overhead_rel"] = (
    (par_mean["Score"] - par_mean["ideal_parallel_time"])
    / par_mean["Score"]
)

# ------------------------------------------------------------
# 6. Tabellen ausgeben
# ------------------------------------------------------------
print("\nSpeedup Tabelle:")
print(
    par_mean[
        ["size", "parallelism", "cutoff", "speedup"]
    ]
)

print("\nEffizienz:")
print(
    par_mean[
        ["size", "parallelism", "cutoff", "efficiency"]
    ]
)

print("\nOverhead:")
print(
    par_mean[
        ["size", "parallelism", "cutoff", "overhead_rel"]
    ]
)

# ------------------------------------------------------------
# 7. Plot: Speedup vs Größe
# ------------------------------------------------------------
plt.figure(figsize=(10,6))

for p in sorted(par_mean["parallelism"].unique()):

    subset = (
        par_mean[
            (par_mean["parallelism"] == p) &
            (par_mean["cutoff"] == 256)
        ]
        .sort_values("size")
    )

    if len(subset) == 0:
        continue

    plt.plot(
        subset["size"],
        subset["speedup"],
        marker="o",
        label=f"p={int(p)}"
    )

plt.xscale("log")
plt.xlabel("Problemgröße (n)")
plt.ylabel("Speedup")
plt.title("Speedup paralleler Merge Sort (cutoff=256)")
plt.legend()
plt.grid(True)
plt.show()

# ------------------------------------------------------------
# 8. Plot: Laufzeiten
# ------------------------------------------------------------
plt.figure(figsize=(10,6))

fixed_cutoff = 256

# Sequential: nur nach size mitteln
seq_plot = seq.groupby("size")["Score"].mean().sort_index()

plt.plot(
    seq_plot.index,
    seq_plot.values,
    marker="o",
    label="Sequential"
)

# Parallel: für jeden parallelism-Wert, aber mit festem cutoff
for p in sorted(par_mean["parallelism"].unique()):
    subset = (
        par_mean[
            (par_mean["parallelism"] == p) &
            (par_mean["cutoff"] == fixed_cutoff)
        ]
        .sort_values("size")
    )

    if len(subset) == 0:
        continue

    plt.plot(
        subset["size"],
        subset["Score"],
        marker="o",
        label=f"Parallel p={int(p)}"
    )

plt.xscale("log")
plt.xlabel("Problemgröße (n)")
plt.ylabel("Zeit (ms/op)")
plt.title(f"Laufzeitvergleich: Sequential vs Parallel Merge Sort (cutoff={fixed_cutoff})")
plt.legend()
plt.grid(True)
plt.show()

# ------------------------------------------------------------
# 9. Plot: Effizienz
# ------------------------------------------------------------
plt.figure(figsize=(10,6))

for p in sorted(par_mean["parallelism"].unique()):

    subset = (
        par_mean[
            (par_mean["parallelism"] == p) &
            (par_mean["cutoff"] == 256)
        ]
        .sort_values("size")
    )

    if len(subset) == 0:
        continue

    plt.plot(
        subset["size"],
        subset["efficiency"],
        marker="o",
        label=f"p={int(p)}"
    )

plt.xscale("log")
plt.xlabel("Problemgröße (n)")
plt.ylabel("Effizienz")
plt.title("Effizienz paralleler Merge Sort")
plt.legend()
plt.grid(True)
plt.show()

# ------------------------------------------------------------
# 10. Plot: Overhead
# ------------------------------------------------------------
plt.figure(figsize=(10,6))

for p in sorted(par_mean["parallelism"].unique()):

    subset = (
        par_mean[
            (par_mean["parallelism"] == p) &
            (par_mean["cutoff"] == 256)
        ]
        .sort_values("size")
    )

    if len(subset) == 0:
        continue

    plt.plot(
        subset["size"],
        subset["overhead_rel"],
        marker="o",
        label=f"p={int(p)}"
    )

plt.xscale("log")
plt.xlabel("Problemgröße (n)")
plt.ylabel("Overhead (relativ)")
plt.title("Parallelisierungs-Overhead (cutoff=256)")
plt.legend()
plt.grid(True)
plt.show()

# ------------------------------------------------------------
# 11. Plot: Einfluss des Cutoff
# ------------------------------------------------------------
plt.figure(figsize=(10,6))

fixed_size = 1000000
fixed_parallelism = 8

subset = (
    par_mean[
        (par_mean["size"] == fixed_size) &
        (par_mean["parallelism"] == fixed_parallelism)
    ]
    .sort_values("cutoff")
)

plt.plot(
    subset["cutoff"],
    subset["Score"],
    marker="o"
)

plt.xticks([0, 256, 512, 1024, 2048, 4096])

plt.xlabel("Cutoff")
plt.ylabel("Zeit (ms/op)")
plt.title(f"Einfluss des Cutoff (n={fixed_size}, p={fixed_parallelism})")
plt.grid(True)
plt.show()