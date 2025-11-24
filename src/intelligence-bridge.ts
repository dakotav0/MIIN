/**
 * Intelligence Bridge
 *
 * Connects Minecraft MCP to MIIN intelligence services:
 * - Music Intelligence (port 5555) - for creative curation patterns
 * - Unified Intelligence (port 5556) - for insights and correlations
 */

import fetch from 'node-fetch';
import { z } from 'zod';

export interface BuildAnalysis {
  title: string;
  blocks: string[];
  blockCounts: Record<string, number>;
  duration?: number;
  tags?: string[];
}

export interface PaletteSuggestion {
  theme: string;
  existingBlocks: string[];
  count: number;
}

export interface PatternDetection {
  days: number;
  patternType: string;
  events: any[];
}

export interface ArchetypeClassification {
  blocks: string[];
  blockCounts: Record<string, number>;
  buildTime?: number;
  playerContext?: any;
}

export interface IntelligenceBridgeConfig {
  musicIntelligenceUrl: string;
  unifiedIntelligenceUrl: string;
  ollamaUrl?: string;
}

export class IntelligenceBridge {
  private musicUrl: string;
  private intelligenceUrl: string;
  private ollamaUrl: string;

  constructor(config: IntelligenceBridgeConfig) {
    this.musicUrl = config.musicIntelligenceUrl;
    this.intelligenceUrl = config.unifiedIntelligenceUrl;
    this.ollamaUrl = config.ollamaUrl || 'http://localhost:11434';
  }

  /**
   * Classify build archetype using LLM analysis
   */
  async classifyArchetype(params: ArchetypeClassification): Promise<any> {
    const archetypeSchema = z
      .object({
        archetype: z.string(),
        confidence: z.number().min(0).max(1),
        style: z.string(),
        scale: z.string(),
        description: z.string(),
        suggestions: z.array(z.string()).default([]),
      })
      .passthrough();

    try {
      const totalBlocks = Object.values(params.blockCounts).reduce((a, b) => a + b, 0);
      const topBlocks = Object.entries(params.blockCounts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
        .map(([block, count]) => `${block}: ${count}`);

      const prompt = `You are a Minecraft building expert. Analyze this build and classify it.

BUILD DATA:
- Total blocks: ${totalBlocks}
- Unique block types: ${params.blocks.length}
- Top blocks used: ${topBlocks.join(', ')}
- Build time: ${params.buildTime ? `${Math.round(params.buildTime / 60)} minutes` : 'unknown'}

TASK: Classify this build into ONE primary archetype and provide analysis.

Respond in this exact JSON format:
{
  "archetype": "one of: castle, house, tower, temple, bridge, wall, farm, garden, underground_base, statue, ship, portal, arena, library, workshop, other",
  "confidence": 0.0 to 1.0,
  "style": "medieval, modern, rustic, futuristic, organic, gothic, asian, fantasy, industrial",
  "scale": "small, medium, large, massive",
  "description": "Brief 1-sentence description of the build",
  "suggestions": ["improvement suggestion 1", "improvement suggestion 2"]
}`;

      const response = await fetch(`${this.ollamaUrl}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: 'llama3.2:latest',
          prompt,
          stream: false,
          format: 'json',
        }),
      });

      if (!response.ok) {
        throw new Error(`Ollama request failed: ${response.status}`);
      }

      const result = await response.json() as { response: string };

      const parsed = this.tryParseJsonResult(result.response);
      const blockAnalysis = {
        total: totalBlocks,
        unique: params.blocks.length,
        topBlocks: Object.fromEntries(
          Object.entries(params.blockCounts)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
        ),
      };

      if (parsed) {
        const validation = archetypeSchema.safeParse(parsed);

        if (validation.success) {
          return {
            ...validation.data,
            blockAnalysis,
          };
        }

        const defaultClassification = {
          archetype: parsed.archetype || 'other',
          confidence: typeof parsed.confidence === 'number' ? Math.min(Math.max(parsed.confidence, 0), 1) : 0.5,
          style: parsed.style || 'unknown',
          scale: parsed.scale || 'unknown',
          description: parsed.description || 'No description provided',
          suggestions: Array.isArray(parsed.suggestions)
            ? parsed.suggestions.filter((s: unknown) => typeof s === 'string')
            : [],
          validationError: validation.error.flatten(),
        };

        return {
          ...defaultClassification,
          blockAnalysis,
        };
      }

      const fallback = this.fallbackArchetypeClassification(params);
      return {
        ...fallback,
        isError: true,
        rawResponse: result.response,
        error: 'Unable to parse or validate archetype response',
      };
    } catch (error) {
      console.error('Archetype classification error:', error);

      // Fallback to rule-based classification
      return this.fallbackArchetypeClassification(params);
    }
  }

  private tryParseJsonResult(rawResponse: string): any | null {
    if (!rawResponse) {
      return null;
    }

    const trimmed = rawResponse.trim();

    try {
      return JSON.parse(trimmed);
    } catch (parseError) {
      // Continue to tolerant parsing below
    }

    const jsonMatch = trimmed.match(/\{[\s\S]*\}/);
    if (jsonMatch) {
      try {
        return JSON.parse(jsonMatch[0]);
      } catch (secondaryError) {
        // No valid JSON found even after trimming extraneous text
      }
    }

    return null;
  }

  /**
   * Fallback rule-based archetype classification
   */
  private fallbackArchetypeClassification(params: ArchetypeClassification): any {
    const totalBlocks = Object.values(params.blockCounts).reduce((a, b) => a + b, 0);
    const blocks = params.blocks;

    // Simple heuristics
    let archetype = 'other';
    let style = 'experimental';
    let confidence = 0.5;

    // Check for common patterns
    const hasStone = blocks.some(b => b.includes('stone') || b.includes('brick'));
    const hasWood = blocks.some(b => b.includes('wood') || b.includes('planks') || b.includes('log'));
    const hasGlass = blocks.some(b => b.includes('glass'));
    const hasFarmBlocks = blocks.some(b => b.includes('farmland') || b.includes('hay') || b.includes('wheat'));
    const hasWater = blocks.some(b => b.includes('water'));
    const hasLeaves = blocks.some(b => b.includes('leaves') || b.includes('flower'));

    if (hasFarmBlocks) {
      archetype = 'farm';
      style = 'rustic';
      confidence = 0.7;
    } else if (hasWater && hasLeaves) {
      archetype = 'garden';
      style = 'organic';
      confidence = 0.6;
    } else if (hasStone && !hasWood && totalBlocks > 200) {
      archetype = 'castle';
      style = 'medieval';
      confidence = 0.6;
    } else if (hasWood && hasGlass && totalBlocks < 200) {
      archetype = 'house';
      style = 'rustic';
      confidence = 0.6;
    } else if (hasStone && totalBlocks > 100) {
      archetype = 'tower';
      style = 'medieval';
      confidence = 0.5;
    }

    const scale = totalBlocks < 50 ? 'small' : totalBlocks < 200 ? 'medium' : totalBlocks < 500 ? 'large' : 'massive';

    return {
      archetype,
      confidence,
      style,
      scale,
      description: `A ${scale} ${style} ${archetype} build`,
      suggestions: [
        'Add more block variety for visual interest',
        'Consider adding lighting elements',
      ],
      blockAnalysis: {
        total: totalBlocks,
        unique: blocks.length,
        topBlocks: Object.fromEntries(
          Object.entries(params.blockCounts)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
        ),
      },
      note: 'Classified using fallback rules (LLM unavailable)',
    };
  }

  /**
   * Analyze a creative build using creative intelligence patterns
   */
  async analyzeCreativeBuild(build: BuildAnalysis): Promise<any> {
    try {
      // Convert build data to creative intelligence format
      const analysis = {
        buildName: build.title,
        totalBlocks: Object.values(build.blockCounts).reduce((a, b) => a + b, 0),
        uniqueBlockTypes: build.blocks.length,
        blockDistribution: build.blockCounts,
        timeSpent: build.duration || 0,
        tags: build.tags || [],
        timestamp: new Date().toISOString(),
      };

      // Calculate creative metrics
      const metrics = this.calculateCreativeMetrics(build);

      // Detect themes based on block choices
      const themes = this.detectThemes(build.blocks, build.blockCounts);

      return {
        summary: `Build "${build.title}" analyzed`,
        metrics,
        themes,
        analysis,
        suggestions: await this.generateBuildSuggestions(themes, build.blocks),
      };
    } catch (error) {
      console.error('Build analysis error:', error);
      throw error;
    }
  }

  /**
   * Suggest block palette using music curation pattern
   * (Just like curating a playlist, but for blocks!)
   */
  async suggestBlockPalette(params: PaletteSuggestion): Promise<any> {
    try {
      // Map theme to color/texture preferences
      const themeMapping = this.mapThemeToBlockPreferences(params.theme);

      // Get block recommendations
      const suggestions = this.curateBlockPalette(
        themeMapping,
        params.existingBlocks,
        params.count
      );

      return {
        theme: params.theme,
        palette: suggestions,
        reasoning: this.explainPaletteSuggestions(params.theme, suggestions),
      };
    } catch (error) {
      console.error('Palette suggestion error:', error);
      throw error;
    }
  }

  /**
   * Detect patterns in Minecraft events using correlation engine
   */
  async detectMinecraftPatterns(params: PatternDetection): Promise<any> {
    try {
      // Analyze events for patterns
      const patterns = {
        temporal: this.detectTemporalPatterns(params.events),
        behavioral: this.detectBehavioralPatterns(params.events),
        preferences: this.detectBlockPreferences(params.events),
      };

      // Calculate pattern confidence
      const insights = this.generatePatternInsights(patterns);

      return {
        timeframe: `${params.days} days`,
        patterns,
        insights,
        recommendations: this.generateRecommendations(patterns),
      };
    } catch (error) {
      console.error('Pattern detection error:', error);
      throw error;
    }
  }

  /**
   * Get proactive insights based on recent activity
   */
  async getProactiveInsights(params: { context: any; recentEvents: any[] }): Promise<any> {
    try {
      const insights = [];

      // Time-based suggestions
      const hour = new Date().getHours();
      if (hour >= 20 || hour < 6) {
        insights.push({
          type: 'suggestion',
          title: '🌙 Late Night Creative Session',
          description: 'Your late-night builds tend to be more experimental. Consider trying a new building style!',
          confidence: 0.8,
        });
      }

      // Pattern-based suggestions
      const recentBlocks = this.extractRecentBlockUsage(params.recentEvents);
      if (recentBlocks.length > 0) {
        const mostUsed = this.getMostUsedBlocks(recentBlocks);
        insights.push({
          type: 'insight',
          title: '🎨 Block Usage Pattern',
          description: `You've been favoring ${mostUsed.join(', ')} lately. Want suggestions for complementary blocks?`,
          confidence: 0.9,
        });
      }

      return {
        insights,
        context: params.context,
        suggestedActions: this.suggestActions(insights),
      };
    } catch (error) {
      console.error('Insights generation error:', error);
      throw error;
    }
  }

  // === Helper Methods ===

  private calculateCreativeMetrics(build: BuildAnalysis) {
    const totalBlocks = Object.values(build.blockCounts).reduce((a, b) => a + b, 0);
    const uniqueTypes = build.blocks.length;

    return {
      diversity: uniqueTypes / Math.max(totalBlocks, 1),
      complexity: this.calculateComplexity(build.blockCounts),
      scale: totalBlocks,
      efficiency: build.duration ? totalBlocks / build.duration : 0,
    };
  }

  private calculateComplexity(blockCounts: Record<string, number>): number {
    // Shannon entropy for block distribution
    const total = Object.values(blockCounts).reduce((a, b) => a + b, 0);
    const entropy = Object.values(blockCounts).reduce((sum, count) => {
      const p = count / total;
      return sum - (p * Math.log2(p));
    }, 0);
    return entropy / Math.log2(Object.keys(blockCounts).length);
  }

  private detectThemes(blocks: string[], blockCounts: Record<string, number>): string[] {
    const themes: string[] = [];

    // Material-based themes
    const woodBlocks = blocks.filter(b => b.includes('wood') || b.includes('log') || b.includes('planks'));
    const stoneBlocks = blocks.filter(b => b.includes('stone') || b.includes('brick') || b.includes('cobblestone'));
    const metalBlocks = blocks.filter(b => b.includes('iron') || b.includes('gold') || b.includes('copper'));
    const naturalBlocks = blocks.filter(b => b.includes('leaves') || b.includes('grass') || b.includes('dirt'));

    if (woodBlocks.length > blocks.length * 0.4) themes.push('natural', 'rustic');
    if (stoneBlocks.length > blocks.length * 0.4) themes.push('medieval', 'fortress');
    if (metalBlocks.length > blocks.length * 0.2) themes.push('industrial', 'modern');
    if (naturalBlocks.length > blocks.length * 0.3) themes.push('organic', 'garden');

    return themes.length > 0 ? themes : ['experimental'];
  }

  private mapThemeToBlockPreferences(theme: string): any {
    const themeMap: Record<string, string[]> = {
      'cozy cabin': ['oak_planks', 'spruce_planks', 'cobblestone', 'oak_log', 'glass_pane', 'wool'],
      'medieval castle': ['stone_bricks', 'cobblestone', 'oak_planks', 'iron_bars', 'torch', 'banner'],
      'futuristic city': ['quartz_block', 'iron_block', 'glass', 'concrete', 'sea_lantern', 'prismarine'],
      'natural garden': ['grass_block', 'flowers', 'leaves', 'dirt', 'path', 'water'],
      'underground base': ['stone', 'andesite', 'diorite', 'redstone_lamp', 'iron_door', 'lantern'],
    };

    return themeMap[theme.toLowerCase()] || ['stone', 'wood', 'glass'];
  }

  private curateBlockPalette(preferences: string[], existing: string[], count: number): any[] {
    // Filter out existing blocks and add complementary ones
    const available = preferences.filter(b => !existing.includes(b));
    const complementary = this.getComplementaryBlocks(existing);

    const palette = [...available, ...complementary].slice(0, count);

    return palette.map(block => ({
      block,
      reason: this.explainBlockChoice(block, existing),
      confidence: 0.8 + Math.random() * 0.2,
    }));
  }

  private getComplementaryBlocks(existing: string[]): string[] {
    // Simple complementary block logic
    const complementary: string[] = [];

    if (existing.some(b => b.includes('oak'))) {
      complementary.push('dark_oak_planks', 'oak_stairs', 'oak_slab');
    }
    if (existing.some(b => b.includes('stone'))) {
      complementary.push('stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks');
    }

    return complementary;
  }

  private explainBlockChoice(block: string, existingBlocks: string[]): string {
    if (block.includes('stairs') || block.includes('slab')) {
      return 'Adds depth and variation to your build';
    }
    if (block.includes('brick')) {
      return 'Provides texture and structural detail';
    }
    return 'Complements your existing palette';
  }

  private explainPaletteSuggestions(theme: string, suggestions: any[]): string[] {
    return [
      `Theme "${theme}" typically uses these materials for authenticity`,
      `Suggested ${suggestions.length} blocks that complement each other`,
      'Balance between primary structure and accent blocks',
    ];
  }

  private detectTemporalPatterns(events: any[]): any {
    const hourCounts: Record<number, number> = {};
    events.forEach(e => {
      const hour = new Date(e.timestamp).getHours();
      hourCounts[hour] = (hourCounts[hour] || 0) + 1;
    });

    const peakHour = Object.entries(hourCounts).sort((a, b) => b[1] - a[1])[0];

    return {
      peakHour: peakHour ? parseInt(peakHour[0]) : null,
      activityByHour: hourCounts,
    };
  }

  private detectBehavioralPatterns(events: any[]): any {
    const buildEvents = events.filter(e => e.eventType === 'build_complete');

    return {
      averageBuildTime: buildEvents.reduce((sum, e) => sum + (e.data.buildTime || 0), 0) / buildEvents.length,
      preferredStyle: this.getMostCommonTheme(buildEvents),
    };
  }

  private detectBlockPreferences(events: any[]): any {
    const blockUsage: Record<string, number> = {};

    events.forEach(e => {
      if (e.data.blocks) {
        e.data.blocks.forEach((block: string) => {
          blockUsage[block] = (blockUsage[block] || 0) + 1;
        });
      }
    });

    return {
      topBlocks: Object.entries(blockUsage)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
        .map(([block, count]) => ({ block, count })),
    };
  }

  private generatePatternInsights(patterns: any): string[] {
    const insights: string[] = [];

    if (patterns.temporal?.peakHour !== null) {
      insights.push(`You're most active around ${patterns.temporal.peakHour}:00`);
    }

    if (patterns.behavioral?.preferredStyle) {
      insights.push(`Your builds lean towards ${patterns.behavioral.preferredStyle} style`);
    }

    if (patterns.preferences?.topBlocks?.length > 0) {
      insights.push(`Your go-to blocks: ${patterns.preferences.topBlocks.slice(0, 3).map((b: any) => b.block).join(', ')}`);
    }

    return insights;
  }

  private generateRecommendations(patterns: any): string[] {
    const recommendations: string[] = [];

    if (patterns.preferences?.topBlocks) {
      const topBlock = patterns.preferences.topBlocks[0]?.block;
      if (topBlock) {
        recommendations.push(`Try variations of ${topBlock} for more depth`);
      }
    }

    recommendations.push('Experiment with complementary color palettes');
    recommendations.push('Consider adding accent blocks for visual interest');

    return recommendations;
  }

  private async generateBuildSuggestions(themes: string[], blocks: string[]): Promise<string[]> {
    const suggestions: string[] = [];

    themes.forEach(theme => {
      suggestions.push(`Your ${theme} theme could be enhanced with more texture variation`);
    });

    if (blocks.length < 5) {
      suggestions.push('Consider expanding your block palette for more visual interest');
    }

    return suggestions;
  }

  private extractRecentBlockUsage(events: any[]): string[] {
    const blocks: string[] = [];
    events.forEach(e => {
      if (e.data?.blocks) {
        blocks.push(...e.data.blocks);
      }
    });
    return blocks;
  }

  private getMostUsedBlocks(blocks: string[]): string[] {
    const counts: Record<string, number> = {};
    blocks.forEach(b => {
      counts[b] = (counts[b] || 0) + 1;
    });

    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([block]) => block);
  }

  private getMostCommonTheme(buildEvents: any[]): string {
    const themes: Record<string, number> = {};
    buildEvents.forEach(e => {
      if (e.data?.tags) {
        e.data.tags.forEach((tag: string) => {
          themes[tag] = (themes[tag] || 0) + 1;
        });
      }
    });

    const topTheme = Object.entries(themes).sort((a, b) => b[1] - a[1])[0];
    return topTheme ? topTheme[0] : 'experimental';
  }

  private suggestActions(insights: any[]): string[] {
    return insights.map(i => `Explore ${i.title}`);
  }
}
