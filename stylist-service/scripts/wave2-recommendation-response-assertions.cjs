'use strict';

const GENERATION_KEYS = ['request_id', 'request_status', 'recommendations', 'limitations'];
const REQUEST_READ_KEYS = ['request_id', 'request_status', 'recommendations'];
const RECOMMENDATION_KEYS = ['recommendation_id', 'explanation', 'styling_tips', 'generated_at', 'items'];
const GENERATION_ITEM_KEYS = ['source_type', 'shop_variant_id', 'slot_name', 'selection_reason', 'display_order'];
const REQUEST_ITEM_KEYS = [
  'source_type', 'wardrobe_item_id', 'shop_variant_id', 'slot_name', 'selection_reason', 'display_order'
];
const DETAIL_KEYS = [
  'recommendation_id', 'request_id', 'explanation', 'styling_tips', 'generated_at',
  'total_current_price_vnd', 'price_semantics', 'items'
];
const DETAIL_ITEM_KEYS = [
  'source_type', 'wardrobe_item_id', 'shop_variant_id', 'slot_name', 'selection_reason', 'display_order',
  'current_availability', 'product_id', 'category_id', 'brand_id', 'product_name', 'category_name',
  'brand_name', 'color', 'size', 'material', 'current_unit_price_vnd', 'images'
];
const COMMON_RECOMMENDATION_FIELDS = [
  'recommendation_id', 'explanation', 'styling_tips', 'generated_at'
];
const COMMON_ITEM_FIELDS = [
  'source_type', 'shop_variant_id', 'slot_name', 'selection_reason', 'display_order'
];
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function unique(paths) {
  return [...new Set(paths)];
}

function structuralDifferencePaths(expected, actual, path = '$', output = []) {
  if (output.length >= 100) return output;
  if (Array.isArray(expected) || Array.isArray(actual)) {
    if (!Array.isArray(expected) || !Array.isArray(actual)) {
      output.push(path);
      return output;
    }
    if (expected.length !== actual.length) output.push(path + '.length');
    for (let index = 0; index < Math.min(expected.length, actual.length) && output.length < 100; index++) {
      structuralDifferencePaths(expected[index], actual[index], path + '[' + index + ']', output);
    }
    return output;
  }
  if (isObject(expected) || isObject(actual)) {
    if (!isObject(expected) || !isObject(actual)) {
      output.push(path);
      return output;
    }
    const keys = [...new Set([...Object.keys(expected), ...Object.keys(actual)])].sort();
    for (const key of keys) {
      if (output.length >= 100) break;
      if (!Object.hasOwn(expected, key) || !Object.hasOwn(actual, key)) output.push(path + '.' + key);
      else structuralDifferencePaths(expected[key], actual[key], path + '.' + key, output);
    }
    return output;
  }
  if (!Object.is(expected, actual)) output.push(path);
  return output;
}

function checkKeys(value, allowed, path, failures) {
  if (!isObject(value)) {
    failures.push(path);
    return;
  }
  for (const key of allowed) if (!Object.hasOwn(value, key)) failures.push(path + '.' + key);
  for (const key of Object.keys(value)) if (!allowed.includes(key)) failures.push(path + '.' + key);
}

function checkUuid(value, path, failures) {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) failures.push(path);
}

function checkNonBlank(value, path, failures) {
  if (typeof value !== 'string' || !value.trim()) failures.push(path);
}

function checkTips(value, path, failures) {
  if (value !== null && typeof value !== 'string') failures.push(path);
}

function checkTimestamp(value, path, failures) {
  if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) failures.push(path);
}

function checkGeneratedItems(items, path, allowedVariants, failures, responseKind) {
  if (!Array.isArray(items)) {
    failures.push(path);
    return;
  }
  if (items.length !== 2) failures.push(path + '.length');
  const slots = new Set();
  const displayOrders = new Set();
  items.forEach((item, index) => {
    const itemPath = path + '[' + index + ']';
    checkKeys(item, responseKind === 'generation' ? GENERATION_ITEM_KEYS : REQUEST_ITEM_KEYS, itemPath, failures);
    if (item.source_type !== 'SHOP_VARIANT') failures.push(itemPath + '.source_type');
    checkUuid(item.shop_variant_id, itemPath + '.shop_variant_id', failures);
    if (allowedVariants && !allowedVariants.has(item.shop_variant_id)) failures.push(itemPath + '.shop_variant_id');
    if (!['TOP', 'BOTTOM'].includes(item.slot_name)) failures.push(itemPath + '.slot_name');
    slots.add(item.slot_name);
    checkNonBlank(item.selection_reason, itemPath + '.selection_reason', failures);
    if (!Number.isInteger(item.display_order) || item.display_order < 1 || item.display_order > 2) {
      failures.push(itemPath + '.display_order');
    }
    displayOrders.add(item.display_order);
    if (responseKind === 'request-read' && item.wardrobe_item_id !== null) {
      failures.push(itemPath + '.wardrobe_item_id');
    }
  });
  if (slots.size !== 2 || !slots.has('TOP') || !slots.has('BOTTOM')) failures.push(path + '.slot_name');
  if (displayOrders.size !== items.length) failures.push(path + '.display_order');
}

function checkRecommendationArray(response, path, allowedVariants, failures, responseKind) {
  if (!Array.isArray(response)) {
    failures.push(path);
    return;
  }
  if (response.length < 1 || response.length > 3) failures.push(path + '.length');
  response.forEach((recommendation, index) => {
    const recommendationPath = path + '[' + index + ']';
    checkKeys(recommendation, RECOMMENDATION_KEYS, recommendationPath, failures);
    checkUuid(recommendation.recommendation_id, recommendationPath + '.recommendation_id', failures);
    checkNonBlank(recommendation.explanation, recommendationPath + '.explanation', failures);
    checkTips(recommendation.styling_tips, recommendationPath + '.styling_tips', failures);
    checkTimestamp(recommendation.generated_at, recommendationPath + '.generated_at', failures);
    checkGeneratedItems(recommendation.items, recommendationPath + '.items', allowedVariants, failures, responseKind);
  });
}

function compareFields(expected, actual, fields, path, failures) {
  for (const field of fields) if (!Object.is(expected?.[field], actual?.[field])) failures.push(path + '.' + field);
}

function validateGenerationResponse(response, options = {}) {
  const failures = [];
  const expectedRequestId = options.requestId || response?.request_id;
  const allowedVariants = options.allowedVariantIds ? new Set(options.allowedVariantIds) : null;
  checkKeys(response, GENERATION_KEYS, '$', failures);
  checkUuid(response?.request_id, '$.request_id', failures);
  if (response?.request_id !== expectedRequestId) failures.push('$.request_id');
  if (response?.request_status !== 'SUCCEEDED') failures.push('$.request_status');
  if (!Array.isArray(response?.limitations) || response.limitations.some(value => typeof value !== 'string')) {
    failures.push('$.limitations');
  }
  checkRecommendationArray(response?.recommendations, '$.recommendations', allowedVariants, failures, 'generation');
  return unique(failures);
}

function validateRequestReadResponse(response, options = {}) {
  const failures = [];
  checkKeys(response, REQUEST_READ_KEYS, '$', failures);
  checkUuid(response?.request_id, '$.request_id', failures);
  if (response?.request_id !== options.requestId) failures.push('$.request_id');
  if (response?.request_status !== 'SUCCEEDED') failures.push('$.request_status');
  checkRecommendationArray(response?.recommendations, '$.recommendations',
    options.allowedVariantIds ? new Set(options.allowedVariantIds) : null, failures, 'request-read');
  return unique(failures);
}

function compareGeneratedWithRequestRead(generated, readBack, options = {}) {
  const generationFailurePaths = validateGenerationResponse(generated, options);
  const requestReadFailurePaths = validateRequestReadResponse(readBack,
    { ...options, requestId: options.requestId || generated?.request_id });
  const crossResponseFailurePaths = [];
  const generatedRecommendations = Array.isArray(generated?.recommendations) ? generated.recommendations : [];
  const readRecommendations = Array.isArray(readBack?.recommendations) ? readBack.recommendations : [];
  if (generated?.request_id !== readBack?.request_id) crossResponseFailurePaths.push('$.request_id');
  if (generated?.request_status !== readBack?.request_status) crossResponseFailurePaths.push('$.request_status');
  if (generatedRecommendations.length !== readRecommendations.length) crossResponseFailurePaths.push('$.recommendations.length');
  for (let index = 0; index < Math.min(generatedRecommendations.length, readRecommendations.length); index++) {
    const generationRecommendation = generatedRecommendations[index];
    const readRecommendation = readRecommendations[index];
    const recommendationPath = '$.recommendations[' + index + ']';
    compareFields(generationRecommendation, readRecommendation, COMMON_RECOMMENDATION_FIELDS,
      recommendationPath, crossResponseFailurePaths);
    const generatedItems = Array.isArray(generationRecommendation?.items) ? generationRecommendation.items : [];
    const readItems = Array.isArray(readRecommendation?.items) ? readRecommendation.items : [];
    if (generatedItems.length !== readItems.length) crossResponseFailurePaths.push(recommendationPath + '.items.length');
    for (let itemIndex = 0; itemIndex < Math.min(generatedItems.length, readItems.length); itemIndex++) {
      compareFields(generatedItems[itemIndex], readItems[itemIndex], COMMON_ITEM_FIELDS,
        recommendationPath + '.items[' + itemIndex + ']', crossResponseFailurePaths);
    }
  }
  const failurePaths = unique([
    ...generationFailurePaths,
    ...requestReadFailurePaths,
    ...crossResponseFailurePaths
  ]);
  return {
    valid: failurePaths.length === 0,
    failurePaths,
    generationFailurePaths,
    requestReadFailurePaths,
    crossResponseFailurePaths: unique(crossResponseFailurePaths),
    structuralDifferencePaths: structuralDifferencePaths(generated, readBack)
  };
}

function compareGenerationReplay(generated, replay, options = {}) {
  const failures = [
    ...validateGenerationResponse(generated, options),
    ...validateGenerationResponse(replay, { ...options, requestId: options.requestId || generated?.request_id })
  ];
  failures.push(...structuralDifferencePaths(generated, replay));
  return { valid: unique(failures).length === 0, failurePaths: unique(failures) };
}

function compareGeneratedWithDetail(generated, detail, options = {}) {
  const failures = validateGenerationResponse(generated, options);
  const expectedRequestId = options.requestId || generated?.request_id;
  const generatedRecommendations = Array.isArray(generated?.recommendations) ? generated.recommendations : [];
  checkKeys(detail, DETAIL_KEYS, '$', failures);
  checkUuid(detail?.recommendation_id, '$.recommendation_id', failures);
  checkUuid(detail?.request_id, '$.request_id', failures);
  if (detail?.request_id !== expectedRequestId) failures.push('$.request_id');
  if (generatedRecommendations.length !== 1) failures.push('$.recommendations.length');
  const expectedRecommendation = generatedRecommendations[0];
  if (detail?.recommendation_id !== expectedRecommendation?.recommendation_id) failures.push('$.recommendation_id');
  compareFields(expectedRecommendation, detail, ['explanation', 'styling_tips', 'generated_at'], '$', failures);
  if (detail?.price_semantics !== 'CURRENT_COMMERCE_DATA_NOT_HISTORICAL') failures.push('$.price_semantics');
  if (!Number.isSafeInteger(detail?.total_current_price_vnd) || detail.total_current_price_vnd < 0) {
    failures.push('$.total_current_price_vnd');
  }

  const generatedItems = Array.isArray(expectedRecommendation?.items) ? expectedRecommendation.items : [];
  const detailItems = Array.isArray(detail?.items) ? detail.items : [];
  if (!Array.isArray(detail?.items)) failures.push('$.items');
  if (detailItems.length !== generatedItems.length) failures.push('$.items.length');
  const priceByVariant = options.currentPriceByVariant || {};
  let expectedTotal = 0;
  for (let index = 0; index < Math.min(generatedItems.length, detailItems.length); index++) {
    const generatedItem = generatedItems[index];
    const detailItem = detailItems[index];
    const itemPath = '$.items[' + index + ']';
    checkKeys(detailItem, DETAIL_ITEM_KEYS, itemPath, failures);
    compareFields(generatedItem, detailItem, COMMON_ITEM_FIELDS, itemPath, failures);
    if (detailItem?.wardrobe_item_id !== null) failures.push(itemPath + '.wardrobe_item_id');
    if (detailItem?.current_availability !== 'AVAILABLE') failures.push(itemPath + '.current_availability');
    const expectedPrice = priceByVariant[generatedItem?.shop_variant_id];
    if (!Number.isSafeInteger(expectedPrice) || detailItem?.current_unit_price_vnd !== expectedPrice) {
      failures.push(itemPath + '.current_unit_price_vnd');
    } else expectedTotal += expectedPrice;
    if (!Array.isArray(detailItem?.images)) failures.push(itemPath + '.images');
  }
  if (Number.isSafeInteger(options.maxBudgetVnd) && detail?.total_current_price_vnd > options.maxBudgetVnd) {
    failures.push('$.total_current_price_vnd');
  }
  if (Object.keys(priceByVariant).length > 0 && detail?.total_current_price_vnd !== expectedTotal) {
    failures.push('$.total_current_price_vnd');
  }
  return { valid: unique(failures).length === 0, failurePaths: unique(failures) };
}

module.exports = {
  compareGeneratedWithDetail,
  compareGeneratedWithRequestRead,
  compareGenerationReplay,
  structuralDifferencePaths,
  validateGenerationResponse,
  validateRequestReadResponse
};
