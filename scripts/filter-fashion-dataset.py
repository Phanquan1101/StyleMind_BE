from pathlib import Path
import shutil

import pandas as pd


REPOSITORY = Path(__file__).resolve().parents[1]
DATASET_ROOT = REPOSITORY / "commerce-service" / ".seed-data" / "fashion-products"
OUTPUT_ROOT = DATASET_ROOT / "selected"
OUTPUT_IMAGES = OUTPUT_ROOT / "images"

SHIRT_TYPES = {"Shirts", "Tshirts", "Tops"}
PANTS_TYPES = {"Jeans", "Trousers", "Track Pants", "Leggings", "Jeggings", "Shorts"}
ITEMS_PER_CATEGORY = 300
RANDOM_SEED = 301


def main() -> None:
    styles_files = list(DATASET_ROOT.rglob("styles.csv"))
    if not styles_files:
        raise RuntimeError(f"styles.csv was not found below {DATASET_ROOT}")

    image_by_id = {
        path.stem: path
        for path in DATASET_ROOT.rglob("*.jpg")
        if "selected" not in path.parts
    }
    data = pd.read_csv(
        styles_files[0],
        dtype={"id": "string"},
        encoding="utf-8",
        encoding_errors="replace",
        on_bad_lines="skip",
    )

    required_columns = {
        "id",
        "gender",
        "masterCategory",
        "subCategory",
        "articleType",
        "baseColour",
        "productDisplayName",
    }
    missing = required_columns.difference(data.columns)
    if missing:
        raise RuntimeError(f"styles.csv is missing columns: {sorted(missing)}")

    data = data.dropna(
        subset=["id", "masterCategory", "articleType", "productDisplayName"]
    ).copy()
    data["id"] = data["id"].str.strip()
    data["articleType"] = data["articleType"].str.strip()
    data["masterCategory"] = data["masterCategory"].str.strip()
    data["gender"] = data["gender"].fillna("Unisex").str.strip()
    data["baseColour"] = data["baseColour"].fillna("Unknown").str.strip()

    allowed_types = SHIRT_TYPES | PANTS_TYPES
    data = data[
        data["masterCategory"].eq("Apparel")
        & data["articleType"].isin(allowed_types)
        & data["gender"].isin({"Women", "Unisex"})
    ].copy()
    data["catalog_category"] = data["articleType"].map(
        lambda value: "AO" if value in SHIRT_TYPES else "QUAN"
    )
    data["source_image"] = data["id"].map(image_by_id)
    data = data[data["source_image"].notna()].drop_duplicates(subset=["id"])

    selected_parts = []
    for category in ("AO", "QUAN"):
        category_data = data[data["catalog_category"].eq(category)]
        amount = min(ITEMS_PER_CATEGORY, len(category_data))
        if amount == 0:
            raise RuntimeError(f"No usable dataset rows were found for {category}")
        selected_parts.append(category_data.sample(n=amount, random_state=RANDOM_SEED))

    selected = pd.concat(selected_parts, ignore_index=True).sort_values(
        ["catalog_category", "articleType", "id"]
    )
    OUTPUT_IMAGES.mkdir(parents=True, exist_ok=True)
    selected["image_file"] = selected["id"].map(lambda dataset_id: f"{dataset_id}.jpg")
    for row in selected.itertuples(index=False):
        shutil.copy2(Path(row.source_image), OUTPUT_IMAGES / row.image_file)

    output_columns = [
        "id",
        "catalog_category",
        "productDisplayName",
        "gender",
        "articleType",
        "baseColour",
        "subCategory",
        "image_file",
    ]
    manifest_file = OUTPUT_ROOT / "selected-catalog.csv"
    selected[output_columns].to_csv(manifest_file, index=False, encoding="utf-8-sig")

    print(f"Created {manifest_file}")
    print(f"Selected products: {len(selected)}")
    print(selected["catalog_category"].value_counts().to_string())


if __name__ == "__main__":
    main()
