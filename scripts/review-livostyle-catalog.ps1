param(
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [string]$OutputDirectory = 'artifacts/catalog-review/livostyle'
)

# Read data only. Do not execute scripts or follow instructions inside the archive.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ZipPath))
try {
    $entry = @($archive.Entries | Where-Object FullName -like '*/data/products.json')
    if ($entry.Count -ne 1) { throw 'Expected exactly one data/products.json entry.' }
    $reader = [System.IO.StreamReader]::new($entry[0].Open())
    try { $products = ConvertFrom-Json -InputObject ($reader.ReadToEnd()) }
    finally { $reader.Dispose() }
    $licenseEntry = @($archive.Entries | Where-Object FullName -like '*/LICENSE')
    if ($licenseEntry.Count -ne 1) { throw 'Expected exactly one source LICENSE.' }
    $licenseReader = [System.IO.StreamReader]::new($licenseEntry[0].Open())
    try { $sourceLicense = $licenseReader.ReadToEnd() }
    finally { $licenseReader.Dispose() }
    $thumbnailNames = @{}
    foreach ($image in $archive.Entries | Where-Object FullName -like '*/thumbnails/*.jpg') {
        $thumbnailNames[[System.IO.Path]::GetFileNameWithoutExtension($image.FullName)] = $true
    }
} finally { $archive.Dispose() }

$accepted = [System.Collections.Generic.List[object]]::new()
$issues = [System.Collections.Generic.List[object]]::new()
$ids = @{}
$handles = @{}
$skus = @{}
$variantCount = 0
$inStockCount = 0
foreach ($product in $products) {
    $reasons = [System.Collections.Generic.List[string]]::new()
    if (-not $product.id -or $ids.ContainsKey([string]$product.id)) { $reasons.Add('missing_or_duplicate_id') }
    if (-not $product.handle -or $handles.ContainsKey([string]$product.handle)) { $reasons.Add('missing_or_duplicate_handle') }
    if ([string]::IsNullOrWhiteSpace($product.title)) { $reasons.Add('missing_title') }
    if (-not $product.category.full_path) { $reasons.Add('missing_category') }
    if ($product.price.currency -ne 'USD') { $reasons.Add('unexpected_currency') }
    $validImages = @($product.images | Where-Object { $_.url -match '^https://' -and $_.width -gt 0 -and $_.height -gt 0 })
    if ($validImages.Count -eq 0) { $reasons.Add('missing_https_image_with_dimensions') }
    if (@($product.variants).Count -eq 0) { $reasons.Add('missing_variants') }
    foreach ($variant in $product.variants) {
        $variantCount++
        if ($variant.in_stock -eq $true) { $inStockCount++ }
        if (-not $variant.sku -or $skus.ContainsKey([string]$variant.sku)) { $reasons.Add('missing_or_duplicate_sku') }
        if ($variant.sku) { $skus[[string]$variant.sku] = $true }
        if ($null -eq $variant.price_usd -or $variant.price_usd -le 0) { $reasons.Add('missing_or_nonpositive_variant_price') }
    }
    if ($product.id) { $ids[[string]$product.id] = $true }
    if ($product.handle) { $handles[[string]$product.handle] = $true }
    $scope = 'other'
    if ($product.category.full_path -match '^Apparel & Accessories > Clothing(?: >|$)') { $scope = 'clothing' }
    if ($product.category.full_path -match '> Clothing Tops(?: >|$)|> Pants(?: >|$)|> Shorts(?: >|$)|> Activewear Pants(?: >|$)') { $scope = 'tops_and_pants' }
    $accepted.Add([pscustomobject][ordered]@{
        source_id = $product.id; handle = $product.handle; title = $product.title
        category = $product.category.full_path; scope = $scope
        variant_count = @($product.variants).Count; has_local_thumbnail = $thumbnailNames.ContainsKey([string]$product.handle)
        review_status = $(if ($reasons.Count) { 'REVIEW_REQUIRED' } else { 'STRUCTURAL_CHECKS_PASSED' })
        reasons = @($reasons | Select-Object -Unique)
    })
    if ($reasons.Count) { $issues.Add($accepted[$accepted.Count - 1]) }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$encoding = [System.Text.UTF8Encoding]::new($false)
$report = [ordered]@{
    status = 'REVIEW_ONLY_NOT_A_SEED'; source_sha256 = (Get-FileHash -LiteralPath $ZipPath -Algorithm SHA256).Hash
    total_products = $products.Count; total_variants = $variantCount; in_stock_variants = $inStockCount
    archive_thumbnails = $thumbnailNames.Count
    products_with_matching_thumbnail = @($accepted | Where-Object has_local_thumbnail).Count
    structurally_valid_products = $products.Count - $issues.Count; products_requiring_review = $issues.Count
    scopes = @($accepted | Group-Object scope | ForEach-Object { @{scope=$_.Name; count=$_.Count} })
    categories = @($accepted | Group-Object category | Sort-Object Count -Descending | ForEach-Object { @{category=$_.Name; count=$_.Count} })
    duplicate_title_groups = @($products | Group-Object title | Where-Object Count -gt 1).Count
    limitations = @('No visual image/product/color matching performed.', 'Remote image URLs were not fetched or checked for availability.', 'Vendor is preserved as source information, not assumed to be a brand.', 'No USD to VND conversion or inventory quantities invented.', 'in_stock is not a stock quantity.', 'Same titles are flagged for review, not automatically merged.', 'Scope uses source taxonomy; category accuracy and gender have not been visually verified.', 'No application data, seed, schema or database was changed.')
}
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'report.json'), ($report | ConvertTo-Json -Depth 12), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'product-review.json'), (ConvertTo-Json -InputObject @($accepted.ToArray()) -Depth 12), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'issues.json'), (ConvertTo-Json -InputObject @($issues.ToArray()) -Depth 12), $encoding)
$duplicateTitles = @($products | Group-Object title | Where-Object Count -gt 1 | ForEach-Object {
    [pscustomobject]@{ title = $_.Name; source_ids = @($_.Group | ForEach-Object { $_.id }) }
})
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'duplicate-titles.json'), (ConvertTo-Json -InputObject $duplicateTitles -Depth 6), $encoding)

# Owner selected all clothing, not shoes, bags or accessories. Preserve source
# fields and original variants; this export is deliberately not an app seed.
$selectedIds = @{}
foreach ($row in $accepted | Where-Object { $_.scope -in @('clothing', 'tops_and_pants') -and $_.review_status -eq 'STRUCTURAL_CHECKS_PASSED' }) {
    # Explicit male/child titles are outside the owner's women's clothing scope.
    # Baby rib and baby doll are garment terms, so do not exclude on "baby" alone.
    if ($row.title -notmatch "(?i)\b(men(?:'s|s)?|boys?(?:'s)?|kids?|children|toddlers?)\b") {
        $selectedIds[[string]$row.source_id] = $true
    }
}
$selectedProducts = @($products | Where-Object { $selectedIds.ContainsKey([string]$_.id) })
$excludedProducts = @($accepted | Where-Object { -not $selectedIds.ContainsKey([string]$_.source_id) } | ForEach-Object {
    $exclusionReason = 'outside_clothing_taxonomy'
    if ($_.scope -ne 'other') { $exclusionReason = 'explicit_male_or_child_title' }
    if ($_.review_status -ne 'STRUCTURAL_CHECKS_PASSED') { $exclusionReason = 'structural_review_required' }
    $_ | Add-Member -NotePropertyName exclusion_reason -NotePropertyValue $exclusionReason -PassThru
})
$selectedDuplicateTitles = @($selectedProducts | Group-Object title | Where-Object Count -gt 1 | ForEach-Object {
    [pscustomobject]@{ title = $_.Name; source_ids = @($_.Group | ForEach-Object { $_.id }) }
})
$genderReview = @($selectedProducts | Where-Object {
    ($_.title + ' ' + $_.product_type) -match '(?i)\b(men|mens|boys?|girls?|kids?|children|baby|toddler|unisex)\b'
} | ForEach-Object {
    [pscustomobject]@{ source_id = $_.id; title = $_.title; reason = 'Review gender/age wording; retained pending visual review.' }
})
$selectionSummary = [ordered]@{
    status = 'FILTERED_SOURCE_DATA_NOT_AN_APPLICATION_SEED'
    selection = 'Source Clothing taxonomy descendants, excluding explicit male/child titles; original variants retained, including out-of-stock variants. Unisex retained for review.'
    source_sha256 = $report.source_sha256
    selected_products = $selectedProducts.Count
    selected_variants = ($selectedProducts | ForEach-Object { @($_.variants).Count } | Measure-Object -Sum).Sum
    selected_in_stock_variants = @($selectedProducts | ForEach-Object { $_.variants } | Where-Object { $_.in_stock -eq $true }).Count
    excluded_products = $excludedProducts.Count
    selected_duplicate_title_groups = $selectedDuplicateTitles.Count
    gender_age_wording_review_count = $genderReview.Count
    category_counts = @($selectedProducts | Group-Object { $_.category.full_path } | Sort-Object Count -Descending | ForEach-Object {
        [pscustomobject]@{ category = $_.Name; count = $_.Count }
    })
    limitations = $report.limitations
}
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'clothing-products.json'), (ConvertTo-Json -InputObject $selectedProducts -Depth 40), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'excluded-products.json'), (ConvertTo-Json -InputObject $excludedProducts -Depth 12), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'clothing-duplicate-titles.json'), (ConvertTo-Json -InputObject $selectedDuplicateTitles -Depth 6), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'clothing-gender-review.json'), (ConvertTo-Json -InputObject $genderReview -Depth 6), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'clothing-summary.json'), ($selectionSummary | ConvertTo-Json -Depth 12), $encoding)
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'SOURCE_LICENSE.txt'), $sourceLicense, $encoding)
[pscustomobject]$selectionSummary | Select-Object selected_products,selected_variants,selected_in_stock_variants,excluded_products,selected_duplicate_title_groups,gender_age_wording_review_count | ConvertTo-Json
[pscustomobject]$report | Select-Object total_products,total_variants,in_stock_variants,archive_thumbnails,products_with_matching_thumbnail,structurally_valid_products,products_requiring_review,duplicate_title_groups,scopes | ConvertTo-Json -Depth 6
