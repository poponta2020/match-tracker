import { useDraggable } from '@dnd-kit/core';
import PlayerChip from '../../components/PlayerChip';

const DraggablePlayerChip = ({ id, data, disabled = false, onClick, isSelected = false }) => {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id,
    data,
    disabled,
  });

  const handleClick = (e) => {
    e.stopPropagation();
    if (onClick) onClick(e);
  };

  const chipClass = isSelected
    ? 'text-sm bg-[#4a6b5a] text-white border-2 border-[#2d4a3e] cursor-pointer select-none'
    : 'text-sm bg-[#f9f6f2] text-[#374151] cursor-grab active:cursor-grabbing select-none';

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      onClick={handleClick}
      style={{ opacity: isDragging ? 0.4 : 1, touchAction: 'none' }}
    >
      <PlayerChip
        name={data.playerName}
        kyuRank={data.kyuRank}
        className={chipClass}
      />
    </div>
  );
};

export default DraggablePlayerChip;
