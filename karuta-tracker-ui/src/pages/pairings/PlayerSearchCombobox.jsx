import { useState } from 'react';

const PlayerSearchCombobox = ({ players, selectedPlayerId, onSelect }) => {
  const [searchText, setSearchText] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(-1);

  const filteredPlayers = players.filter(
    player => (player.name ?? '').includes(searchText)
  );

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex(prev =>
        prev < filteredPlayers.length - 1 ? prev + 1 : 0
      );
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex(prev =>
        prev > 0 ? prev - 1 : filteredPlayers.length - 1
      );
    } else if (e.key === 'Enter' && highlightedIndex >= 0 && highlightedIndex < filteredPlayers.length) {
      e.preventDefault();
      const player = filteredPlayers[highlightedIndex];
      setSearchText(player.name);
      onSelect(String(player.id));
    }
  };

  const handleSelect = (player, index) => {
    setSearchText(player.name);
    setHighlightedIndex(index);
    onSelect(String(player.id));
  };

  const handleChange = (e) => {
    setSearchText(e.target.value);
    setHighlightedIndex(-1);
    onSelect('');
  };

  return (
    <div className="mb-4">
      <label id="player-search-label" className="block text-sm font-medium text-gray-700 mb-2">
        選手を選択
      </label>
      <input
        type="text"
        role="combobox"
        aria-expanded="true"
        aria-controls="player-listbox"
        aria-labelledby="player-search-label"
        aria-activedescendant={highlightedIndex >= 0 && filteredPlayers[highlightedIndex] ? `player-option-${filteredPlayers[highlightedIndex].id}` : undefined}
        value={searchText}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder="選手名を入力して検索..."
        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
        autoFocus
      />
      <div id="player-listbox" role="listbox" aria-labelledby="player-search-label" className="mt-2 max-h-48 overflow-y-auto border border-gray-200 rounded-lg">
        {filteredPlayers.map((player, index) => (
          <div
            key={player.id}
            id={`player-option-${player.id}`}
            role="option"
            aria-selected={String(player.id) === selectedPlayerId}
            onClick={() => handleSelect(player, index)}
            className={`w-full text-left px-4 py-2 text-sm cursor-pointer hover:bg-[#e8f0eb] transition-colors ${
              String(player.id) === selectedPlayerId ? 'bg-[#e8f0eb] font-semibold text-[#4a6b5a]' : index === highlightedIndex ? 'bg-[#f0f5f2] text-[#4a6b5a]' : 'text-gray-700'
            }`}
          >
            {player.name} ({player.kyuRank || player.danRank || '初心者'})
          </div>
        ))}
        {filteredPlayers.length === 0 && (
          <div className="px-4 py-3 text-sm text-gray-400 text-center">
            該当する選手がいません
          </div>
        )}
      </div>
    </div>
  );
};

export default PlayerSearchCombobox;
