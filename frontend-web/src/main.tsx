import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import Layout from "./components/Layout";
import DashboardPage from "./pages/DashboardPage";
import ProjectListPage from "./pages/ProjectListPage";
import ProjectDetailPage from "./pages/ProjectDetailPage";
import ReleaseDetailPage from "./pages/ReleaseDetailPage";
import TargetEnvListPage from "./pages/TargetEnvListPage";
import "./index.css";

const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "projects", element: <ProjectListPage /> },
      { path: "projects/:id", element: <ProjectDetailPage /> },
      { path: "releases/:id", element: <ReleaseDetailPage /> },
      { path: "target-envs", element: <TargetEnvListPage /> },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);