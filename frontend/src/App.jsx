import { useEffect, useState } from "react";
import { AdminHomePage } from "@/pages/AdminHome/AdminHomePage.jsx";
import { AdminLoginPage } from "@/pages/AdminLogin/AdminLoginPage.jsx";
import { AdminClassesPage } from "@/pages/AdminClasses/AdminClassesPage.jsx";
import { AdminClassDetailsPage } from "@/pages/AdminClasses/AdminClassDetailsPage.jsx";
import { AdminRequestsPage } from "@/pages/AdminRequests/AdminRequestsPage.jsx";
import { AdminUsersPage } from "@/pages/AdminUsers/AdminUsersPage.jsx";
import { AdminSubjectsPage } from "@/pages/AdminSubjects/AdminSubjectsPage.jsx";
import { AdminSchedulePage } from "@/pages/AdminSchedule/AdminSchedulePage.jsx";
import { ForgetPasswordPage } from "@/pages/ForgetPassword/ForgetPasswordPage.jsx";
import { LoginPage } from "@/pages/Login/LoginPage.jsx";
import { RequestRegistrationPage } from "@/pages/RequestRegistration/RequestRegistrationPage.jsx";
import { ResetPasswordPage } from "@/pages/ResetPassword/ResetPasswordPage.jsx";
import { StudentHomePage } from "@/pages/StudentHome/StudentHomePage.jsx";
import { TeacherHomePage } from "@/pages/TeacherHome/TeacherHomePage.jsx";
import { ParentHomePage } from "@/pages/ParentHome/ParentHomePage.jsx";
import { TermsOfUsePage } from "@/pages/TermsOfUse/TermsOfUsePage.jsx";

function getRouteFromHash() {
  return window.location.hash.replace("#", "") || "login";
}

export default function App() {
  const [route, setRoute] = useState(getRouteFromHash);

  useEffect(() => {
    const handleHashChange = () => setRoute(getRouteFromHash());

    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  if (route === "request-registration") {
    return <RequestRegistrationPage />;
  }

  if (route === "forgot-password") {
    return <ForgetPasswordPage />;
  }

  if (route === "terms-of-use") {
    return <TermsOfUsePage />;
  }

  if (route === "reset-password" || route.startsWith("reset-password?")) {
    return <ResetPasswordPage key={route} token={new URLSearchParams(route.split("?")[1] || "").get("token") || ""} />;
  }

  if (route === "admin") {
    return <AdminLoginPage />;
  }

  if (route === "admin/home") {
    return <AdminHomePage />;
  }

  if (route === "admin/requests") {
    return <AdminRequestsPage />;
  }

  if (route === "admin/users") {
    return <AdminUsersPage />;
  }

  if (route === "admin/classes") {
    return <AdminClassesPage />;
  }

  if (route === "admin/subjects") {
    return <AdminSubjectsPage />;
  }

  if (route === "admin/schedule") {
    return <AdminSchedulePage />;
  }

  const classRoute = /^admin\/classes\/(\d+)(\/schedule)?$/.exec(route);
  if (classRoute) {
    return <AdminClassDetailsPage key={route} classId={Number(classRoute[1])} scheduleView={Boolean(classRoute[2])} />;
  }

  if (route === "student") {
    return <StudentHomePage />;
  }

  if (route === "teacher") {
    return <TeacherHomePage />;
  }

  if (route === "parent") {
    return <ParentHomePage />;
  }

  return <LoginPage />;
}
