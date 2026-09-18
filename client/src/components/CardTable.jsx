import { useMemo, useRef, useState } from 'react';
import { creaturePower, creatureToughness, formatManaValue, releaseDate } from '../lib/cardFormatting';
import { sortCards } from '../lib/sorting';

const CARD_PREVIEW_WIDTH = 250;
const CARD_PREVIEW_ASPECT_RATIO = 88 / 63; // Standard Magic card height:width ratio
const CARD_PREVIEW_MARGIN = 12;

function CardName({ card }) {
  const anchorRef = useRef(null);
  const [previewStyle, setPreviewStyle] = useState(null);
  const hasImage = card.matchedScryfallCard && card.scryfallId;

  function showPreview() {
    if (!hasImage || !anchorRef.current) return;
    const rect = anchorRef.current.getBoundingClientRect();
    const previewHeight = CARD_PREVIEW_WIDTH * CARD_PREVIEW_ASPECT_RATIO;

    // Center the preview on the row, but clamp it inside the viewport so it is
    // never cut off — including when the row sits near the top of the page,
    // where anchoring purely below/above the row would push it off-screen.
    const maxTop = Math.max(window.innerHeight - previewHeight - CARD_PREVIEW_MARGIN,
      CARD_PREVIEW_MARGIN);
    const idealTop = rect.top + rect.height / 2 - previewHeight / 2;
    const top = Math.min(Math.max(idealTop, CARD_PREVIEW_MARGIN), maxTop);

    const spaceOnRight = window.innerWidth - rect.right;
    const idealLeft = spaceOnRight >= CARD_PREVIEW_WIDTH + CARD_PREVIEW_MARGIN
      ? rect.right + CARD_PREVIEW_MARGIN
      : rect.left - CARD_PREVIEW_WIDTH - CARD_PREVIEW_MARGIN;
    const maxLeft = Math.max(window.innerWidth - CARD_PREVIEW_WIDTH - CARD_PREVIEW_MARGIN,
      CARD_PREVIEW_MARGIN);
    const left = Math.min(Math.max(idealLeft, CARD_PREVIEW_MARGIN), maxLeft);

    setPreviewStyle({ top, left });
  }

  function hidePreview() {
    setPreviewStyle(null);
  }

  return (
    <span ref={anchorRef} className={`card-name${hasImage ? ' has-image' : ''}`}
      tabIndex={hasImage ? 0 : undefined}
      onMouseEnter={showPreview} onMouseLeave={hidePreview}
      onFocus={showPreview} onBlur={hidePreview}>
      <strong>{card.name}</strong>
      {previewStyle && (
        <span className="card-preview" role="tooltip"
          style={{ top: `${previewStyle.top}px`, left: `${previewStyle.left}px` }}>
          <img src={`/cards/${encodeURIComponent(card.scryfallId)}/image`}
            alt={card.name} loading="lazy" />
        </span>
      )}
    </span>
  );
}

const CARD_COLUMNS = [
  { key: 'name', label: 'Name', sort: 'name', className: 'col-name' },
  { key: 'cardType', label: 'Card Type', sort: 'cardType', className: 'col-type' },
  { key: 'manaValue', label: 'Mana Value', sort: 'manaValue', className: 'col-mv' },
  { key: 'power', label: 'Power', sort: 'power', className: 'col-pt' },
  { key: 'toughness', label: 'Toughness', sort: 'toughness', className: 'col-pt' },
  { key: 'releaseDate', label: 'Release Date', sort: 'releaseDate', className: 'col-date' },
];

function SortableHeader({ columns, sort, onSort, editable }) {
  return (
    <thead>
      <tr>
        <th className="col-qty" scope="col">Qty</th>
        {columns.map((column) => {
          const active = sort.key === column.sort;
          const arrow = active ? (sort.direction === 'asc' ? ' ▲' : ' ▼') : '';
          return (
            <th key={column.key} scope="col" className={column.className}>
              <button type="button" className={`sort-header${active ? ' active' : ''}`}
                onClick={() => onSort(column.sort)}
                aria-label={`Sort by ${column.label}`}>
                {column.label}{arrow}
              </button>
            </th>
          );
        })}
        {editable && <th className="col-edit" scope="col">Edit</th>}
      </tr>
    </thead>
  );
}

export function CardTable({ cards, editable, onAdd, onRemove }) {
  const [sort, setSort] = useState({ key: 'name', direction: 'asc' });

  const sorted = useMemo(
    () => sortCards(cards || [], sort.key, sort.direction),
    [cards, sort.key, sort.direction],
  );

  function toggleSort(key) {
    setSort((current) => (current.key === key
      ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' }
      : { key, direction: 'asc' }));
  }

  return (
    <table className="card-table">
      <SortableHeader columns={CARD_COLUMNS} sort={sort} onSort={toggleSort} editable={editable} />
      <tbody>
        {sorted.map((card, index) => (
          <tr className="card-row" key={`${card.scryfallId || card.name}-${index}`}>
            <td className="col-qty quantity">{card.quantity}×</td>
            <td className="col-name">
              <CardName card={card} />
              {!card.matchedScryfallCard && <span className="unmatched"> Not in Scryfall</span>}
            </td>
            <td className="col-type">{card.typeLine || 'Unknown type'}</td>
            <td className="col-mv mana-value">{formatManaValue(card.manaValue)}</td>
            <td className="col-pt">{creaturePower(card) ?? '—'}</td>
            <td className="col-pt">{creatureToughness(card) ?? '—'}</td>
            <td className="col-date">{releaseDate(card) || '—'}</td>
            {editable && (
              <td className="col-edit">
                <button type="button" className="pill-button remove"
                  title={`Remove one ${card.name}`}
                  onClick={() => onRemove(card)}>−</button>
                <button type="button" className="pill-button add"
                  title={`Add another ${card.name}`}
                  onClick={() => onAdd(card)}>+</button>
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
