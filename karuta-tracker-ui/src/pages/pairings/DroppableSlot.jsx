import { useDroppable } from '@dnd-kit/core';

const DroppableSlot = ({ id, data, children }) => {
  const { setNodeRef } = useDroppable({
    id,
    data,
  });

  return (
    <div ref={setNodeRef} className="flex-1 min-w-0">
      {children}
    </div>
  );
};

export default DroppableSlot;
