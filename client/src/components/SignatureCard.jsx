import { useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowsRotate } from '@fortawesome/free-solid-svg-icons';
import {
  creaturePower, creatureToughness, formatManaValue, releaseDate,
} from '../lib/cardFormatting';

/** Renders a deck's signature card: image, key metadata, and a flip control for double-faced cards. */
export function SignatureCard({ card }) {
  const [flipped, setFlipped] = useState(false);
  if (!card) return null;
  const canFlip = card.backImageUrl != null && card.backImageUrl !== '';
  const frontSrc = card.scryfallId
    ? `/cards/${encodeURIComponent(card.scryfallId)}/image`
    : card.imageUrl;
  // The back face is served via the same image proxy using the front card's
  // scryfall id with a `?face=back` hint that the proxy resolves to the back
  // image. The proxy falls back to the front image when there is no back face.
  const backSrc = card.scryfallId
    ? `/cards/${encodeURIComponent(card.scryfallId)}/image?face=back`
    : card.backImageUrl;

  return (
    <article className="signature-card">
      <div className={`signature-image ${canFlip ? 'flippable' : ''} ${flipped ? 'flipped' : ''}`}>
        <div className="signature-face signature-front">
          {frontSrc ? (
            <img src={frontSrc} alt={card.name} loading="lazy" />
          ) : (
            <div className="signature-image placeholder">{card.name}</div>
          )}
        </div>
        {canFlip && (
          <div className="signature-face signature-back">
            <img src={backSrc} alt={`${card.name} (back)`} loading="lazy" />
          </div>
        )}
        {canFlip && (
          <button type="button" className="flip-button"
            title={flipped ? 'Show front face' : 'Show back face'}
            aria-label={flipped ? 'Show front face' : 'Show back face'}
            onClick={() => setFlipped((current) => !current)}>
            <FontAwesomeIcon icon={faArrowsRotate} />
          </button>
        )}
      </div>
      <dl className="signature-metadata">
        <div><dt>Name</dt><dd><strong>{card.name}</strong></dd></div>
        <div><dt>Card type</dt><dd>{card.typeLine || 'Unknown type'}</dd></div>
        <div><dt>Mana value</dt><dd>{formatManaValue(card.manaValue)}</dd></div>
        <div><dt>Power</dt><dd>{creaturePower(card) ?? '\u2014'}</dd></div>
        <div><dt>Toughness</dt><dd>{creatureToughness(card) ?? '\u2014'}</dd></div>
        <div><dt>Release date</dt><dd>{releaseDate(card) || '\u2014'}</dd></div>
      </dl>
    </article>
  );
}
