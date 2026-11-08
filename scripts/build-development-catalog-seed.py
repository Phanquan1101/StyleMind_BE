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
ARTICLE_TYPE_VI = {
    "Tshirts": "Áo thun",
    "Tops": "Áo kiểu",
    "Shirts": "Áo sơ mi",
    "Leggings": "Quần legging",
    "Jeggings": "Quần jegging",
    "Shorts": "Quần short",
    "Track Pants": "Quần thể thao",
    "Trousers": "Quần dài",
    "Jeans": "Quần jean",
}
COLOR_VI = {
    "Beige": "Be",
    "Black": "Đen",
    "Blue": "Xanh dương",
    "Brown": "Nâu",
    "Charcoal": "Xám than",
    "Cream": "Kem",
    "Green": "Xanh lá",
    "Grey": "Xám",
    "Grey Melange": "Xám melange",
    "Khaki": "Kaki",
    "Lavender": "Tím oải hương",
    "Maroon": "Đỏ đô",
    "Mustard": "Vàng mù tạt",
    "Navy Blue": "Xanh navy",
    "Off White": "Trắng ngà",
    "Olive": "Xanh ô liu",
    "Orange": "Cam",
    "Peach": "Hồng đào",
    "Pink": "Hồng",
    "Purple": "Tím",
    "Red": "Đỏ",
    "Rust": "Nâu gỉ",
    "Sea Green": "Xanh ngọc biển",
    "Turquoise Blue": "Xanh ngọc lam",
    "White": "Trắng",
    "Yellow": "Vàng",
}
ALPHA_SIZES = ("S", "M", "L", "XL")
NUMERIC_SIZES = ("26", "28", "30", "32")
COLOR_MODIFIERS = {"bright", "dark", "deep", "dusky", "light", "pale", "rust"}


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


def family_name(name: str, colors: set[str]) -> str:
    result = name
    removable = sorted(colors | COLOR_MODIFIERS, key=len, reverse=True)
    for color in removable:
        result = re.sub(rf"\b{re.escape(color)}\b", " ", result, flags=re.IGNORECASE)
    result = re.sub(r"\s+", " ", result).strip(" -&/")
    return result or name


def vietnamese_audience(gender: str) -> str:
    return "nữ" if gender == "Women" else "dành cho mọi giới"


def vietnamese_product_name(brand: str, article_type: str, gender: str, model_id: str) -> str:
    return f"{brand} - {ARTICLE_TYPE_VI[article_type]} {vietnamese_audience(gender)} - Mẫu {model_id}"


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
    colors = {row["color"].strip().casefold() for row in rows if row["color"].strip()}
    product_groups: dict[tuple[str, str, str, str, str], list[dict[str, str]]] = {}
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
        name = family_name(row["name"], colors)
        group_key = (row["catalog_category"], brand_key, row["gender"], row["article_type"], slug(name))
        product_groups.setdefault(group_key, []).append({**row, "family_name": name})

    products = []
    for group_key, group_rows in product_groups.items():
        group_rows.sort(key=lambda row: int(row["dataset_id"]))
        first = group_rows[0]
        article_type = first["article_type"]
        audience = vietnamese_audience(first["gender"])
        product_name = vietnamese_product_name(
            brands[group_key[1]]["name"], article_type, first["gender"], first["dataset_id"]
        )
        sizes = NUMERIC_SIZES if article_type in {"Jeans", "Trousers", "Shorts"} else ALPHA_SIZES
        rows_by_color: dict[str, list[dict[str, str]]] = {}
        for row in group_rows:
            rows_by_color.setdefault(row["color"].strip().casefold(), []).append(row)

        variants = []
        for color_key, color_rows in sorted(rows_by_color.items()):
            color_rows.sort(key=lambda row: int(row["dataset_id"]))
            representative = color_rows[0]
            for size in sizes:
                dataset_id = representative["dataset_id"]
                variants.append(
                    {
                        "variantId": stable_uuid(f"stylemind:seed:variant:{dataset_id}:{size}"),
                        "sku": f"MYNTRA-{dataset_id}-{size}",
                        "size": size,
                        "color": COLOR_VI[representative["color"]],
                        "material": None,
                        "unitPriceVnd": PRICES[article_type],
                        "onHandQuantity": 20,
                        "images": [
                            {
                                "imageId": stable_uuid(
                                    f"stylemind:seed:variant-image:{row['dataset_id']}:{size}"
                                ),
                                "imageAssetId": row["image_id"],
                                "imageUrl": row["image_url"],
                                "altText": f"{product_name} - Màu {COLOR_VI[row['color']]}",
                                "displayOrder": index,
                                "primary": index == 0,
                            }
                            for index, row in enumerate(color_rows)
                        ],
                    }
                )
        products.append(
            {
                "productId": first["product_id"],
                "categoryId": categories[first["catalog_category"]]["categoryId"],
                "brandId": brands[group_key[1]]["brandId"],
                "name": product_name,
                "description": (
                    f"{ARTICLE_TYPE_VI[article_type]} {audience}, "
                    "thuộc bộ sưu tập phát triển của StyleMind."
                ),
                "demographicSegment": "WOMEN" if first["gender"] == "Women" else "UNISEX",
                "variants": variants,
            }
        )

    manifest = {
        "version": 2,
        "sourceImageCount": len(rows),
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
