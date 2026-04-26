export const VENUE_KADERU = 'KADERU';
export const VENUE_HIGASHI = 'HIGASHI';

export const KADERU_VENUE_IDS = new Set([3, 4, 8, 11]);
export const HIGASHI_VENUE_IDS = new Set();

const toVenueId = (value) => {
  if (value === undefined || value === null || value === '') {
    return null;
  }

  const venueId = Number(value);
  return Number.isInteger(venueId) ? venueId : null;
};

export const resolveVenue = (practiceSession) => {
  const venueId = toVenueId(practiceSession?.venueId ?? practiceSession?.venue_id);

  if (venueId === null) {
    return null;
  }

  if (KADERU_VENUE_IDS.has(venueId)) {
    return VENUE_KADERU;
  }

  if (HIGASHI_VENUE_IDS.has(venueId)) {
    return VENUE_HIGASHI;
  }

  return null;
};

export default resolveVenue;
