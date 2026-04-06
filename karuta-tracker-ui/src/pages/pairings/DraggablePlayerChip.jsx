import { useDraggable } from '@dnd-kit/core';
import PlayerChip from '../../components/PlayerChip';

const DraggablePlayerChip = ({ id, data, disabled = false }) => {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id,
    data,
    disabled,
  });

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={{ opacity: isDragging ? 0.4 : 1, touchAction: 'none' }}
    >
      <PlayerChip
        name={data.playerName}
        kyuRank={data.kyuRank}
        className="text-sm bg-[#f9f6f2] text-[#374151] cursor-grab active:cursor-grabbing select-none"
      />
    </div>
  );
};

export default DraggablePlayerChip;
