import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
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
import { TeacherJournalPage } from "@/pages/TeacherJournal/TeacherJournalPage.jsx";
import { ParentHomePage } from "@/pages/ParentHome/ParentHomePage.jsx";
import { TermsOfUsePage } from "@/pages/TermsOfUse/TermsOfUsePage.jsx";
import { PrivacyPolicyPage } from "@/pages/PrivacyPolicy/PrivacyPolicyPage.jsx";
import { UserSchedulePage } from "@/pages/Schedule/UserSchedulePage.jsx";
import { UserSubjectsPage } from "@/pages/Subjects/UserSubjectsPage.jsx";
import { UserProfilePage } from "@/pages/Profile/UserProfilePage.jsx";
import { ApiError, authApi } from "@/lib/api.js";
import { ThemeToggle } from "@/components/ThemeToggle.jsx";
import { RouteAccessToast } from "@/components/RouteAccessToast.jsx";
import { showRouteAccessNotice } from "@/lib/routeAccessNotice.js";
import { isRouteNavigationBlocked } from "@/lib/navigationGuard.js";

function getRouteFromHash() {
  return window.location.hash.replace("#", "") || "login";
}

const homeRoutes = {
  ADMIN: "admin/home",
  STUDENT: "student",
  TEACHER: "teacher",
  PARENT: "parent",
};

function ProtectedRoute({ allowedRole, loginRoute, children }) {
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let active = true;

    authApi.getSession()
      .then((session) => {
        if (!active) return;

        if (session.role !== allowedRole) {
          showRouteAccessNotice("ACCESS_DENIED");
          window.location.hash = allowedRole === "ADMIN"
            ? loginRoute
            : (homeRoutes[session.role] || loginRoute);
          return;
        }

        setChecking(false);
      })
      .catch((error) => {
        if (!active) return;

        showRouteAccessNotice(error instanceof ApiError && [401, 403].includes(error.status)
          ? "AUTH_REQUIRED"
          : "SESSION_CHECK_FAILED");
        window.location.hash = loginRoute;
      });

    return () => {
      active = false;
    };
  }, [allowedRole, loginRoute]);

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-500">
        <Loader2 className="h-7 w-7 animate-spin" aria-label="Перевірка доступу" />
      </div>
    );
  }

  return children;
}

function AppContent() {
  const [route, setRoute] = useState(getRouteFromHash);
  const routeRef = useRef(route);

  useEffect(() => {
    const handleHashChange = () => {
      const nextRoute = getRouteFromHash();
      const currentRoute = routeRef.current;

      if (isRouteNavigationBlocked(nextRoute, currentRoute)) {
        window.history.replaceState(null, "", `#${currentRoute}`);
        return;
      }

      routeRef.current = nextRoute;
      setRoute(nextRoute);
    };

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

  if (route === "privacy-policy") {
    return <PrivacyPolicyPage />;
  }

  if (route === "reset-password" || route.startsWith("reset-password?")) {
    return <ResetPasswordPage key={route} token={new URLSearchParams(route.split("?")[1] || "").get("token") || ""} />;
  }

  if (route === "admin") {
    return <AdminLoginPage />;
  }

  if (route === "admin/home") {
    return <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin"><AdminHomePage /></ProtectedRoute>;
  }

  if (route === "admin/requests") {
    return <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin"><AdminRequestsPage /></ProtectedRoute>;
  }

  if (route === "admin/users") {
    return <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin"><AdminUsersPage /></ProtectedRoute>;
  }

  if (route === "admin/classes") {
    return <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin"><AdminClassesPage /></ProtectedRoute>;
  }

  if (route === "admin/subjects") {
    return <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin"><AdminSubjectsPage /></ProtectedRoute>;
  }

  const adminScheduleRoute = /^admin\/schedule(?:\?(.*))?$/.exec(route);
  if (adminScheduleRoute) {
    const params = new URLSearchParams(adminScheduleRoute[1] || "");
    const initialClassId = params.get("classId") || "";
    const initialAcademicYear = params.get("academicYear") || "";
    return (
      <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin">
        <AdminSchedulePage initialClassId={initialClassId} initialAcademicYear={initialAcademicYear} />
      </ProtectedRoute>
    );
  }

  const legacyClassScheduleRoute = /^admin\/classes\/(\d+)\/schedule$/.exec(route);
  if (legacyClassScheduleRoute) {
    return (
      <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin">
        <AdminSchedulePage initialClassId={legacyClassScheduleRoute[1]} />
      </ProtectedRoute>
    );
  }

  const classRoute = /^admin\/classes\/(\d+)$/.exec(route);
  if (classRoute) {
    return (
      <ProtectedRoute key={route} allowedRole="ADMIN" loginRoute="admin">
        <AdminClassDetailsPage classId={Number(classRoute[1])} />
      </ProtectedRoute>
    );
  }

  if (route === "student") {
    return <ProtectedRoute key={route} allowedRole="STUDENT" loginRoute="login"><StudentHomePage /></ProtectedRoute>;
  }

  if (route === "student/schedule") {
    return <ProtectedRoute key={route} allowedRole="STUDENT" loginRoute="login"><UserSchedulePage role="STUDENT" /></ProtectedRoute>;
  }

  if (route === "student/subjects") {
    return <ProtectedRoute key={route} allowedRole="STUDENT" loginRoute="login"><UserSubjectsPage role="STUDENT" /></ProtectedRoute>;
  }

  if (route === "student/profile") {
    return <ProtectedRoute key={route} allowedRole="STUDENT" loginRoute="login"><UserProfilePage role="STUDENT" /></ProtectedRoute>;
  }

  if (route === "teacher") {
    return <ProtectedRoute key={route} allowedRole="TEACHER" loginRoute="login"><TeacherHomePage /></ProtectedRoute>;
  }

  if (route === "teacher/schedule") {
    return <ProtectedRoute key={route} allowedRole="TEACHER" loginRoute="login"><UserSchedulePage role="TEACHER" /></ProtectedRoute>;
  }

  if (route === "teacher/journal") {
    return <ProtectedRoute key={route} allowedRole="TEACHER" loginRoute="login"><TeacherJournalPage /></ProtectedRoute>;
  }

  if (route === "teacher/profile") {
    return <ProtectedRoute key={route} allowedRole="TEACHER" loginRoute="login"><UserProfilePage role="TEACHER" /></ProtectedRoute>;
  }

  if (route === "parent") {
    return <ProtectedRoute key={route} allowedRole="PARENT" loginRoute="login"><ParentHomePage /></ProtectedRoute>;
  }

  if (route === "parent/schedule") {
    return <ProtectedRoute key={route} allowedRole="PARENT" loginRoute="login"><UserSchedulePage role="PARENT" /></ProtectedRoute>;
  }

  if (route === "parent/subjects") {
    return <ProtectedRoute key={route} allowedRole="PARENT" loginRoute="login"><UserSubjectsPage role="PARENT" /></ProtectedRoute>;
  }

  if (route === "parent/profile") {
    return <ProtectedRoute key={route} allowedRole="PARENT" loginRoute="login"><UserProfilePage role="PARENT" /></ProtectedRoute>;
  }

  return <LoginPage />;
}

export default function App() {
  return <><AppContent /><RouteAccessToast /><ThemeToggle /></>;
}
