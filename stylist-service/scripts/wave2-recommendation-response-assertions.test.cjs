'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  compareGeneratedWithDetail,
  compareGeneratedWithRequestRead,
  compareGenerationReplay
} = require('./wave2-recommendation-response-assertions.cjs');

const requestId = '71000000-0000-4000-8000-000000000001';
const recommendationId = '72000000-0000-4000-8000-000000000001';
const topVariantId = '63000000-0000-4000-8000-000000000001';
const bottomVariantId = '63000000-0000-4000-8000-000000000002';
const allowedVariantIds = [topVariantId, bottomVariantId];

function generatedResponse() {
  return {
    request_id: requestId,
    request_status: 'SUCCEEDED',
    limitations: [],
    recommendations: [{
      recommendation_id: recommendationId,
      explanation: 'Synthetic explanation.',
      styling_tips: 'Synthetic tips.',
      generated_at: '2026-11-07T10:00:00Z',
      items: [
        { source_type: 'SHOP_VARIANT', shop_variant_id: topVariantId, slot_name: 'TOP', selection_reason: 'Synthetic top reason.', display_order: 1 },
        { source_type: 'SHOP_VARIANT', shop_variant_id: bottomVariantId, slot_name: 'BOTTOM', selection_reason: 'Synthetic bottom reason.', display_order: 2 }
      ]
    }]
  };
}

function requestReadResponse(generated = generatedResponse()) {
  return {
    request_id: generated.request_id,
    request_status: generated.request_status,
    recommendations: generated.recommendations.map(recommendation => ({
      ...recommendation,
      items: recommendation.items.map(item => ({ wardrobe_item_id: null, ...item }))
    }))
  };
}

function detailResponse(generated = generatedResponse()) {
  const recommendation = generated.recommendations[0];
  const products = [
    { product_id: '62000000-0000-4000-8000-000000000001', category_id: '61000000-0000-4000-8000-000000000001', brand_id: '64000000-0000-4000-8000-000000000001', product_name: 'Synthetic top', category_name: 'Synthetic TOP', unitPrice: 250000 },
    { product_id: '62000000-0000-4000-8000-000000000002', category_id: '61000000-0000-4000-8000-000000000002', brand_id: '64000000-0000-4000-8000-000000000001', product_name: 'Synthetic bottom', category_name: 'Synthetic BOTTOM', unitPrice: 300000 }
  ];
  return {
    recommendation_id: recommendation.recommendation_id,
    request_id: generated.request_id,
    explanation: recommendation.explanation,
    styling_tips: recommendation.styling_tips,
    generated_at: recommendation.generated_at,
    total_current_price_vnd: 550000,
    price_semantics: 'CURRENT_COMMERCE_DATA_NOT_HISTORICAL',
    items: recommendation.items.map((item, index) => ({
      source_type: item.source_type,
      wardrobe_item_id: null,
      shop_variant_id: item.shop_variant_id,
      slot_name: item.slot_name,
      selection_reason: item.selection_reason,
      display_order: item.display_order,
      current_availability: 'AVAILABLE',
      product_id: products[index].product_id,
      category_id: products[index].category_id,
      brand_id: products[index].brand_id,
      product_name: products[index].product_name,
      category_name: products[index].category_name,
      brand_name: 'Synthetic Test Brand',
      color: index === 0 ? 'white' : 'black',
      size: index === 0 ? 'S' : 'M',
      material: index === 0 ? null : 'linen',
      current_unit_price_vnd: products[index].unitPrice,
      images: []
    }))
  };
}

function assertPath(result, expectedPath) {
  assert.equal(result.valid, false);
  assert.ok(result.failurePaths.includes(expectedPath), `missing failure path ${expectedPath}: ${JSON.stringify(result.failurePaths)}`);
}

test('accepts the contract-specific GET-list wardrobe null and reports actual structural field paths', () => {
  const generated = generatedResponse();
  const readBack = requestReadResponse(generated);
  const result = compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds });

  assert.equal(result.valid, true, JSON.stringify(result.failurePaths));
  assert.deepEqual(result.failurePaths, []);
  assert.deepEqual(result.structuralDifferencePaths, [
    '$.limitations',
    '$.recommendations[0].items[0].wardrobe_item_id',
    '$.recommendations[0].items[1].wardrobe_item_id'
  ]);
});

test('rejects a changed variant even when it is independently allowed by the whitelist', () => {
  const generated = generatedResponse();
  const readBack = requestReadResponse(generated);
  readBack.recommendations[0].items[0].shop_variant_id = bottomVariantId;
  assertPath(compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds }),
    '$.recommendations[0].items[0].shop_variant_id');
});

test('rejects a variant outside the backend whitelist', () => {
  const generated = generatedResponse();
  const readBack = requestReadResponse(generated);
  readBack.recommendations[0].items[0].shop_variant_id = '63000000-0000-4000-8000-000000000099';
  assertPath(compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds }),
    '$.recommendations[0].items[0].shop_variant_id');
});

test('rejects a missing item', () => {
  const generated = generatedResponse();
  const readBack = requestReadResponse(generated);
  readBack.recommendations[0].items.pop();
  assertPath(compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds }),
    '$.recommendations[0].items.length');
});

test('rejects wrong slot, order, or source type instead of filtering the fields out', () => {
  const mutations = [
    ['slot_name', 'BOTTOM', '$.recommendations[0].items[0].slot_name'],
    ['display_order', 2, '$.recommendations[0].items[0].display_order'],
    ['source_type', 'WARDROBE_ITEM', '$.recommendations[0].items[0].source_type']
  ];
  for (const [field, value, expectedPath] of mutations) {
    const generated = generatedResponse();
    const readBack = requestReadResponse(generated);
    readBack.recommendations[0].items[0][field] = value;
    assertPath(compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds }), expectedPath);
  }
});

test('rejects changed recommendation ID, timestamp and persisted user-facing content', () => {
  const mutations = [
    ['recommendation_id', '72000000-0000-4000-8000-000000000002', '$.recommendations[0].recommendation_id'],
    ['generated_at', '2026-11-07T10:00:01Z', '$.recommendations[0].generated_at'],
    ['explanation', 'Different stored explanation.', '$.recommendations[0].explanation'],
    ['styling_tips', 'Different stored tips.', '$.recommendations[0].styling_tips']
  ];
  for (const [field, value, expectedPath] of mutations) {
    const generated = generatedResponse();
    const readBack = requestReadResponse(generated);
    readBack.recommendations[0][field] = value;
    assertPath(compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds }), expectedPath);
  }
});

test('requires the GET list wardrobe reference to remain explicitly null for a shop-only item', () => {
  const generated = generatedResponse();
  const readBack = requestReadResponse(generated);
  readBack.recommendations[0].items[0].wardrobe_item_id = '73000000-0000-4000-8000-000000000001';
  assertPath(compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds }),
    '$.recommendations[0].items[0].wardrobe_item_id');
});

test('rejects changed request ID and terminal state', () => {
  const generated = generatedResponse();
  const readBack = requestReadResponse(generated);
  readBack.request_id = '71000000-0000-4000-8000-000000000002';
  readBack.request_status = 'PROCESSING';
  const result = compareGeneratedWithRequestRead(generated, readBack, { requestId, allowedVariantIds });
  assertPath(result, '$.request_id');
  assertPath(result, '$.request_status');
});

test('keeps wardrobe_item_id out of the generation response contract', () => {
  const generated = generatedResponse();
  generated.recommendations[0].items[0].wardrobe_item_id = null;
  assertPath(compareGeneratedWithRequestRead(generated, requestReadResponse(generated), { requestId, allowedVariantIds }),
    '$.recommendations[0].items[0].wardrobe_item_id');
});

test('replay compares generation response to generation response including content, timestamp and item identity', () => {
  const generated = generatedResponse();
  assert.equal(compareGenerationReplay(generated, structuredClone(generated), { requestId, allowedVariantIds }).valid, true);
  const replay = structuredClone(generated);
  replay.recommendations[0].items[1].shop_variant_id = topVariantId;
  assertPath(compareGenerationReplay(generated, replay, { requestId, allowedVariantIds }),
    '$.recommendations[0].items[1].shop_variant_id');
});

test('validates detail against its own contract and current Commerce price/status values', () => {
  const generated = generatedResponse();
  const detail = detailResponse(generated);
  const currentPriceByVariant = { [topVariantId]: 250000, [bottomVariantId]: 300000 };
  assert.equal(compareGeneratedWithDetail(generated, detail, {
    requestId, allowedVariantIds, currentPriceByVariant, maxBudgetVnd: 850000
  }).valid, true);

  detail.items[1].current_unit_price_vnd = 350000;
  assertPath(compareGeneratedWithDetail(generated, detail, {
    requestId, allowedVariantIds, currentPriceByVariant, maxBudgetVnd: 850000
  }), '$.items[1].current_unit_price_vnd');
});
