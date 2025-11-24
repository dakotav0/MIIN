/**
 * Build Analysis Tool Handlers
 *
 * Handles build analysis, palette suggestions, pattern detection using IntelligenceBridge
 */

import { createErrorResult, createSuccessResult } from '../utils/python-executor.js';
import { ToolHandler } from '../types.js';
import type { IntelligenceBridge } from '../intelligence-bridge.js';
import type { MinecraftEventTracker } from '../event-tracker.js';
import { assertArgs } from '../utils/assert-args.js';

// Dependencies injected from main module
let intelligenceBridge: IntelligenceBridge;
let eventTracker: MinecraftEventTracker;

/**
 * Initialize build tools with required dependencies
 */
export function initializeBuildTools(
  bridge: IntelligenceBridge,
  tracker: MinecraftEventTracker
) {
  intelligenceBridge = bridge;
  eventTracker = tracker;
}

/**
 * minecraft_analyze_build - Analyze a completed build
 */
export const analyzeBuildHandler: ToolHandler = async (args) => {
  const { buildName, blocks, blockCounts, buildTime, tags } = assertArgs<{
    buildName: string;
    blocks: string[];
    blockCounts?: Record<string, number>;
    buildTime?: number;
    tags?: string[];
  }>(args, ['buildName', 'blocks']);

  try {
    // Track the build event
    eventTracker.trackEvent('build_complete', {
      buildName,
      blocks,
      blockCounts,
      buildTime,
      tags,
      timestamp: new Date().toISOString(),
    });

    // Analyze with creative intelligence
    const analysis = await intelligenceBridge.analyzeCreativeBuild({
      title: buildName,
      blocks,
      blockCounts: blockCounts || {},
      duration: buildTime,
      tags: tags || [],
    });

    return createSuccessResult(analysis);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to analyze build');
  }
};

/**
 * minecraft_suggest_palette - Suggest block palette for theme
 */
export const suggestPaletteHandler: ToolHandler = async (args) => {
  const { theme, existingBlocks, paletteSize } = assertArgs<{
    theme: string;
    existingBlocks?: string[];
    paletteSize?: number;
  }>(args, ['theme']);

  try {
    // Use music intelligence pattern for block curation
    const suggestions = await intelligenceBridge.suggestBlockPalette({
      theme,
      existingBlocks: existingBlocks || [],
      count: paletteSize || 10,
    });

    return createSuccessResult(suggestions);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to suggest palette');
  }
};

/**
 * minecraft_detect_patterns - Detect patterns in build history
 */
export const detectPatternsHandler: ToolHandler = async (args) => {
  const { days, patternType } = assertArgs<{
    days?: number;
    patternType?: string;
  }>(args, []);

  try {
    // Get patterns from event history
    const patterns = await intelligenceBridge.detectMinecraftPatterns({
      days: days || 30,
      patternType: patternType || 'all',
      events: eventTracker.getEvents(days || 30),
    });

    return createSuccessResult(patterns);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to detect patterns');
  }
};

/**
 * minecraft_get_insights - Get proactive insights
 */
export const getInsightsHandler: ToolHandler = async (args) => {
  const { context } = assertArgs<{ context?: any }>(args, []);

  try {
    // Get insights from intelligence bridge
    const insights = await intelligenceBridge.getProactiveInsights({
      context: context || {},
      recentEvents: eventTracker.getEvents(10),
    });

    return createSuccessResult(insights);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to get insights');
  }
};

/**
 * minecraft_classify_archetype - Classify build archetype
 */
export const classifyArchetypeHandler: ToolHandler = async (args) => {
  const { blocks, blockCounts, buildTime } = args as {
    blocks: string[];
    blockCounts: Record<string, number>;
    buildTime?: number;
  };

  try {
    const classification = await intelligenceBridge.classifyArchetype({
      blocks,
      blockCounts,
      buildTime,
    });

    return createSuccessResult(classification);
  } catch (error: any) {
    return createErrorResult(error, 'Failed to classify archetype');
  }
};

/**
 * Build Tool Handler Registry
 */
export const BUILD_HANDLERS = {
  'minecraft_analyze_build': analyzeBuildHandler,
  'minecraft_suggest_palette': suggestPaletteHandler,
  'minecraft_detect_patterns': detectPatternsHandler,
  'minecraft_get_insights': getInsightsHandler,
  'minecraft_classify_archetype': classifyArchetypeHandler,
};
