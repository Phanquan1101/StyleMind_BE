from pathlib import Path
import csv
import json
import re
import unicodedata
import uuid


REPOSITORY = Path(__file__).resolve().parents[1]
SOURCE = (
    REPOSITORY
    / "commerce-service"
    / ".seed-data"
    / "fashion-products"
    / "selected"
    / "cloudinary-catalog.csv"
)
OUTPUT = (
    REPOSITORY
    / "commerce-service"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "seed"
    / "development-catalog.json"
)
NAMESPACE = uuid.UUID("f348d1a7-6abf-49d0-b5cd-8d83d26b4145")

MULTIWORD_BRANDS = (
    "United Colors of Benetton",
    "French Connection",
    "Flying Machine",
    "Allen Solly",
    "Vero Moda",
    "Wills Lifestyle",
    "Jealous 21",
    "Pepe Jeans",
    "Tokyo Talkies",
    "Angry Birds",
)
BRAND_ALIASES = {"ADIDAS": "Adidas", "UCB": "United Colors of Benetton"}
PRICES = {
    "Tshirts": 199_000,
    "Tops": 199_000,
    "Shirts": 249_000,
    "Leggings": 249_000,
    "Jeggings": 249_000,
    "Shorts": 279_000,
    "Track Pants": 329_000,
    "Trousers": 399_000,
    "Jeans": 449_000,
}
ALPHA_SIZES = ("S", "M", "L", "XL")
NUMERIC_SIZES = ("26", "28", "30", "32")


def stable_uuid(value: str) -> str:
    return str(uuid.uuid5(NAMESPACE, value))


def brand_for(name: str) -> str:
    folded = name.casefold()
    for brand in MULTIWORD_BRANDS:
        if folded.startswith(brand.casefold() + " ") or folded == brand.casefold():
            return brand
    first = name.strip().split(maxsplit=1)[0] if name.strip() else "StyleMind Select"
    return BRAND_ALIASES.get(first.upper(), first)


def slug(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", normalized.lower()).strip("-")


def main() -> None:
    if not SOURCE.exists():
        raise RuntimeError(f"Cloudinary manifest does not exist: {SOURCE}")
    with SOURCE.open("r", encoding="utf-8-sig", newline="") as file:
        rows = list(csv.DictReader(file))
    if len(rows) != 600:
        raise RuntimeError(f"Expected 600 Cloudinary rows, found {len(rows)}")

    categories = {
        "AO": {
            "categoryId": stable_uuid("stylemind:seed:category:ao"),
            "name": "Áo",
            "slug": "ao",
        },
        "QUAN": {
            "categoryId": stable_uuid("stylemind:seed:category:quan"),
            "name": "Quần",
            "slug": "quan",
        },
    }
    brands: dict[str, dict] = {}
    products = []
    for row in rows:
        dataset_id = row["dataset_id"]
        brand_name = brand_for(row["name"])
        brand_key = brand_name.casefold()
        brands.setdefault(
            brand_key,
            {
                "brandId": stable_uuid(f"stylemind:seed:brand:{brand_key}"),
                "name": brand_name,
                "slug": slug(brand_name),
            },
        )
        article_type = row["article_type"]
        sizes = NUMERIC_SIZES if article_type in {"Jeans", "Trousers", "Shorts"} else ALPHA_SIZES
        variants = []
        for size in sizes:
            variant_id = stable_uuid(f"stylemind:seed:variant:{dataset_id}:{size}")
            variants.append(
                {
                    "variantId": variant_id,
                    "imageId": stable_uuid(f"stylemind:seed:variant-image:{dataset_id}:{size}"),
                    "sku": f"MYNTRA-{dataset_id}-{size}",
                    "size": size,
                    "color": row["color"],
                    "material": None,
                    "unitPriceVnd": PRICES[article_type],
                    "onHandQuantity": 20,
                }
            )
        products.append(
            {
                "productId": row["product_id"],
                "categoryId": categories[row["catalog_category"]]["categoryId"],
                "brandId": brands[brand_key]["brandId"],
                "name": row["name"],
                "description": f"{article_type} development catalog seed",
                "demographicSegment": "WOMEN" if row["gender"] == "Women" else "UNISEX",
                "imageAssetId": row["image_id"],
                "imageUrl": row["image_url"],
                "variants": variants,
            }
        )

    manifest = {
        "version": 1,
        "categories": sorted(categories.values(), key=lambda item: item["slug"]),
        "brands": sorted(brands.values(), key=lambda item: item["slug"]),
        "products": sorted(products, key=lambda item: item["productId"]),
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Created {OUTPUT}")
    print(f"categories={len(manifest['categories'])}")
    print(f"brands={len(manifest['brands'])}")
    print(f"products={len(manifest['products'])}")
    print(f"variants={sum(len(product['variants']) for product in manifest['products'])}")


if __name__ == "__main__":
    main()
