import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { ChevronRight, Loader2 } from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { ApiError, adminClassesApi } from "@/lib/api.js";
import { AdminLayout } from "@/pages/AdminHome/AdminLayout.jsx";
import { StudentLearningHeader } from "./StudentLearningHeader.jsx";
import { SubjectProgressView } from "@/pages/Subjects/UserSubjectsPage.jsx";

export function AdminStudentLearningPage({ classId, studentId }) {
  const [schoolClass, setSchoolClass] = useState(null);
  const [student, setStudent] = useState(null);
  const [parents, setParents] = useState([]);
  const [parentsError, setParentsError] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    setParents([]);
    setParentsError("");
    Promise.all([
      adminClassesApi.findById(classId),
      adminClassesApi.findStudents(classId),
      adminClassesApi.findStudentParents(classId, studentId)
        .then((data) => ({ data }))
        .catch((requestError) => ({ requestError })),
    ]).then(([classData, students, parentsResult]) => {
      if (!active) return;
      setSchoolClass(classData);
      const selected = students.find((item) => item.id === studentId);
      if (selected) setStudent(selected);
      else setError("Учня не знайдено в цьому класі.");
      if (parentsResult.requestError) {
        setParentsError("Не вдалося завантажити контакти батьків.");
      } else {
        setParents(parentsResult.data);
      }
    }).catch((requestError) => {
      if (active) setError(requestError instanceof ApiError ? requestError.message : "Не вдалося завантажити дані учня.");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [classId, studentId]);

  const className = schoolClass ? `${schoolClass.number}-${schoolClass.letter}` : "Клас";
  return (
    <AdminLayout
      activeItem="Класи"
      title="Навчальні дані"
      subtitle="Оцінки, відвідування та домашні завдання учня."
      breadcrumb={<>
        <Link to="/admin/classes" className="hover:text-slate-900">Класи</Link>
        <ChevronRight className="h-3.5 w-3.5" />
        <Link to={`/admin/classes/${classId}`} className="hover:text-slate-900">{className}</Link>
        <ChevronRight className="h-3.5 w-3.5" />
        <span className="font-medium text-slate-700" aria-current="page">Навчальні дані</span>
      </>}
    >
      {loading ? (
        <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-slate-400" aria-label="Завантаження" /></div>
      ) : error ? (
        <Alert>{error}</Alert>
      ) : student ? (
        <>
          <StudentLearningHeader student={student} className={className} parents={parents} parentsError={parentsError} />
          <SubjectProgressView role="ADMIN" adminStudentId={studentId} initialAcademicYear={schoolClass.academicYear} />
        </>
      ) : null}
    </AdminLayout>
  );
}
