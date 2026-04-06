import { useDroppable } from '@dnd-kit/core';

const DroppableSlot = ({ id, data, children, isDragActive = false }) => {
  const { setNodeRef, isOver } = useDroppable({
    id,
    data,
  });

  const highlightClass = isOver
    ? 'bg-[#d4e4da] rounded-lg'
    : isDragActive
      ? 'bg-[#eef3f0] rounded-lg'
      : '';

  return (
    <div ref={setNodeRef} className={`flex-1 min-w-0 transition-colors ${highlightClass}`}>
      {children}
    </div>
  );
};

export default DroppableSlot;
