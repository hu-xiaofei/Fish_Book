import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FavoriteButton } from '../../favorites/components/FavoriteButton';
import type { FishSummary } from '../model/types';

type FishCardProps = {
  fish: FishSummary;
  from: string;
  isFavorited: boolean;
};

export function FishCard({ fish, from, isFavorited }: FishCardProps) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <article>
      {imageFailed ? (
        <div role="img" aria-label={fish.imageAltText}>暂无鱼类图片</div>
      ) : (
        <img src={fish.imagePath} alt={fish.imageAltText} onError={() => setImageFailed(true)} />
      )}
      <div>
        <h2>{fish.commonNameZh}</h2>
        <p><i>{fish.scientificName}</i></p>
        <p>{fish.familyNameZh}</p>
        {fish.aliases.length > 0 ? <p>别名：{fish.aliases.join('、')}</p> : null}
        {fish.habitats.length > 0 ? <p>栖息环境：{fish.habitats.map((item) => item.labelZh).join('、')}</p> : null}
        <FavoriteButton
          fishSlug={fish.slug}
          isFavorited={isFavorited}
          returnTo={from}
        />
        <Link to={`/fish/${encodeURIComponent(fish.slug)}`} state={{ from }}>查看{fish.commonNameZh}详情</Link>
      </div>
    </article>
  );
}
