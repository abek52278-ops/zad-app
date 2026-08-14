declare module "d3-force-3d" {
  interface CollideForce<NodeType> {
    (alpha: number): void;
    iterations(n: number): CollideForce<NodeType>;
    [key: string]: unknown;
  }
  export function forceCollide<NodeType>(radius?: number | ((node: NodeType) => number)): CollideForce<NodeType>;
}
