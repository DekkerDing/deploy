export interface Project {
  id: number;
  name: string;
  buildType: string;
  sourcePath: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}