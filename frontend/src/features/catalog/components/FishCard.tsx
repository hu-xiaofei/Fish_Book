import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { FishSummary } from '../model/types';

type FishCardProps = {
  fish: FishSummary;
  from: string;
};

export function FishCard({ fish, from }: FishCardProps) {
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
        <Link to={`/fish/${fish.slug}`} state={{ from }}>查看{fish.commonNameZh}详情</Link>
      </div>
    </article>
  );
}
