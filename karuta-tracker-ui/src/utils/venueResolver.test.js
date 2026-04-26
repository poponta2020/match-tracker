import { describe, expect, it } from 'vitest';

import {
  HIGASHI_VENUE_IDS,
  KADERU_VENUE_IDS,
  VENUE_KADERU,
  resolveVenue,
} from './venueResolver';

describe('resolveVenue', () => {
  it.each([3, 4, 8, 11])('maps Kaderu venue id %s to KADERU', (venueId) => {
    expect(resolveVenue({ venueId })).toBe(VENUE_KADERU);
  });

  it('supports snake_case venue_id payloads', () => {
    expect(resolveVenue({ venue_id: 3 })).toBe(VENUE_KADERU);
  });

  it('supports numeric string venue ids', () => {
    expect(resolveVenue({ venueId: '11' })).toBe(VENUE_KADERU);
  });

  it('returns null for unsupported or missing venue ids', () => {
    expect(resolveVenue({ venueId: 6 })).toBeNull();
    expect(resolveVenue({ venueId: 12 })).toBeNull();
    expect(resolveVenue({ venueId: 'abc' })).toBeNull();
    expect(resolveVenue({})).toBeNull();
    expect(resolveVenue(null)).toBeNull();
  });

  it('exports Phase 1 venue id sets', () => {
    expect([...KADERU_VENUE_IDS]).toEqual([3, 4, 8, 11]);
    expect([...HIGASHI_VENUE_IDS]).toEqual([]);
  });
});
