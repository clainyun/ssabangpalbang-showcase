import Svg, { Path, Circle } from 'react-native-svg';

type IconProps = {
  color: string;
};

export function MapIcon({ color }: IconProps) {
  return (
    <Svg
      width={23}
      height={23}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={2.1}
      strokeLinejoin="round"
    >
      <Path d="M9 20l-5 2V6l5-2 6 2 5-2v16l-5 2-6-2z" />
      <Path d="M9 4v16M15 6v16" />
    </Svg>
  );
}

export function CommunityIcon({ color }: IconProps) {
  return (
    <Svg
      width={23}
      height={23}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={2.1}
      strokeLinejoin="round"
    >
      <Path d="M4 5h16v11H9l-4 4v-4H4z" />
    </Svg>
  );
}

export function HomeIcon({ color }: IconProps) {
  return (
    <Svg
      width={23}
      height={23}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={2.1}
      strokeLinejoin="round"
    >
      <Path d="M4 11l8-6 8 6v8a1 1 0 01-1 1h-4v-6H9v6H5a1 1 0 01-1-1z" />
    </Svg>
  );
}

export function WishlistIcon({ color }: IconProps) {
  return (
    <Svg
      width={23}
      height={23}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={2.1}
      strokeLinejoin="round"
    >
      <Path d="M12 20s-7-4.4-7-9a3.8 3.8 0 017-1.8A3.8 3.8 0 0119 11c0 4.6-7 9-7 9z" />
    </Svg>
  );
}

export function MyIcon({ color }: IconProps) {
  return (
    <Svg
      width={23}
      height={23}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={2.1}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <Circle cx={12} cy={8} r={3.2} />
      <Path d="M5.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" />
    </Svg>
  );
}
