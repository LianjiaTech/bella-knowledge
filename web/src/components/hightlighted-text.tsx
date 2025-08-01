export const HighlightedText: React.FC<{
  text: string;
  className?: string;
  keyword: string;
}> = ({ text, className, keyword }) => {
  if (!keyword || keyword.trim() === "") {
    return <div className={className}>{text}</div>;
  }

  const parts = text.split(
    new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "gi"),
  );

  return (
    <div className={className}>
      {parts.map((part, index) =>
        part.toLowerCase() === keyword.toLowerCase() ? (
          <span key={index} className="bg-yellow-200 font-semibold">
            {part}
          </span>
        ) : (
          part
        ),
      )}
    </div>
  );
};
