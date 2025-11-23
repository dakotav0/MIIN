/**
 * Minecraft Event Tracker
 *
 * Stores and retrieves Minecraft events for pattern analysis
 * Uses JSON file storage (similar to conversation_history.json)
 */

import fs from 'fs';
import path from 'path';

export interface MinecraftEvent {
  eventType: string;
  data: any;
  timestamp: string;
}

export class MinecraftEventTracker {
  private events: MinecraftEvent[] = [];
  private eventFile: string;
  private maxEvents: number = 10000; // Keep last 10k events

  constructor(eventFile?: string) {
    this.eventFile = eventFile || path.join(process.cwd(), 'minecraft_events.json');
    this.loadEvents();
  }

  /**
   * Track a new Minecraft event
   */
  trackEvent(eventType: string, data: any): void {
    const event: MinecraftEvent = {
      eventType,
      data,
      timestamp: data.timestamp || new Date().toISOString(),
    };

    this.events.push(event);

    // Keep only recent events
    if (this.events.length > this.maxEvents) {
      this.events = this.events.slice(-this.maxEvents);
    }

    this.saveEvents();
  }

  /**
   * Get events from the last N days
   */
  getEvents(days: number): MinecraftEvent[] {
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - days);

    return this.events.filter(e => new Date(e.timestamp) >= cutoff);
  }

  /**
   * Get the most recent N events
   */
  getRecentEvents(count: number): MinecraftEvent[] {
    return this.events.slice(-count);
  }

  /**
   * Get events by type
   */
  getEventsByType(eventType: string, days?: number): MinecraftEvent[] {
    let events = this.events.filter(e => e.eventType === eventType);

    if (days) {
      const cutoff = new Date();
      cutoff.setDate(cutoff.getDate() - days);
      events = events.filter(e => new Date(e.timestamp) >= cutoff);
    }

    return events;
  }

  /**
   * Get event statistics
   */
  getStats(): any {
    const eventTypeCounts: Record<string, number> = {};
    this.events.forEach(e => {
      eventTypeCounts[e.eventType] = (eventTypeCounts[e.eventType] || 0) + 1;
    });

    return {
      totalEvents: this.events.length,
      eventTypes: eventTypeCounts,
      oldestEvent: this.events[0]?.timestamp,
      newestEvent: this.events[this.events.length - 1]?.timestamp,
    };
  }

  /**
   * Clear all events
   */
  clear(): void {
    this.events = [];
    this.saveEvents();
  }

  /**
   * Load events from file
   */
  private loadEvents(): void {
    try {
      if (fs.existsSync(this.eventFile)) {
        const data = fs.readFileSync(this.eventFile, 'utf-8');
        this.events = JSON.parse(data);
        console.error(`Loaded ${this.events.length} Minecraft events from ${this.eventFile}`);
      }
    } catch (error) {
      console.error('Error loading events:', error);
      this.events = [];
    }
  }

  /**
   * Save events to file
   */
  private saveEvents(): void {
    try {
      fs.writeFileSync(this.eventFile, JSON.stringify(this.events, null, 2));
    } catch (error) {
      console.error('Error saving events:', error);
    }
  }
}
