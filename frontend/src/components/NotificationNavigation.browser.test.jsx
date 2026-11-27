import { createRoot } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { NotificationButton } from "./NotificationButton.jsx";
import { RecentMessagesCard } from "./RecentMessagesCard.jsx";
import { TeacherNavigation } from "./TeacherNavigation.jsx";
import { LearnerNavigation } from "./LearnerNavigation.jsx";
import { CurrentUserBadge, CurrentUserContext } from "@/lib/currentUser.jsx";
import { messagesApi, userNotificationApi, teacherClassApi, teacherStudentsApi, learnerTeachersApi, parentApi, authApi } from "@/lib/api.js";
import { TeacherClassPage } from "@/pages/TeacherClass/TeacherClassPage.jsx";
import { TeacherStudentsPage } from "@/pages/TeacherStudents/TeacherStudentsPage.jsx";
import { UserTeachersPage } from "@/pages/Teachers/UserTeachersPage.jsx";
import { StudentLearningHeader } from "@/pages/AdminClasses/StudentLearningHeader.jsx";
import { setNavigationRouter } from "@/lib/navigation.js";

let root,router,container;
const originalUrl=window.location.href;
afterEach(()=>{root?.unmount();router?.dispose();container?.remove();vi.restoreAllMocks();window.history.replaceState(null,"",originalUrl);});
function render(component,role="STUDENT") {
  window.history.replaceState(null,"",`/${role.toLowerCase()}`);
  container=document.createElement("div");document.body.append(container);
  router=createMemoryRouter([{path:"*",element:<CurrentUserContext.Provider value={{session:{role,login:"test"},profile:{firstName:"Іван",lastName:"Тест",professional:{homeroomClasses:[{id:1}]}}}}>{component}</CurrentUserContext.Provider>}],{initialEntries:[`/${role.toLowerCase()}`]});
  setNavigationRouter(router);root=createRoot(container);root.render(<RouterProvider router={router} />);
}

test("opens a notification target without a hash and marks it as read",async()=>{
  vi.spyOn(userNotificationApi,"feed").mockResolvedValue({unreadCount:1,items:[{id:1,read:false,title:"Нова оцінка",body:"Математика",target:"student/subjects?subjectId=2",createdAt:new Date().toISOString()}]});
  const read=vi.spyOn(userNotificationApi,"markRead").mockResolvedValue(null);
  render(<NotificationButton />);
  await page.getByRole("button",{name:/Сповіщення/}).click();
  const link=page.getByRole("link",{name:/Нова оцінка/});
  await expect.element(link).toHaveAttribute("href","/student/subjects?subjectId=2");
  await link.click();
  expect(read).toHaveBeenCalledWith(1);
  await expect.poll(()=>router.state.location.search).toBe("?subjectId=2");
});

test("notification preferences open the parent profile",async()=>{
  vi.spyOn(userNotificationApi,"feed").mockResolvedValue({unreadCount:0,items:[]});
  render(<NotificationButton />,"PARENT");
  await page.getByRole("button",{name:/Сповіщення/}).click();
  await expect.element(page.getByText("Сповіщень поки немає")).toBeVisible();
  await page.getByRole("link",{name:"Налаштувати"}).click();
  await expect.poll(()=>router.state.location.pathname+router.state.location.search).toBe("/parent/profile?notifications=1");
});

test("recent messages preserve the selected message ID",async()=>{
  vi.spyOn(messagesApi,"list").mockResolvedValue({unreadCount:1,messages:{content:[{id:9,read:false,subject:"Зустріч",sender:{fullName:"Учитель"}}]}});
  render(<RecentMessagesCard role="PARENT" />,"PARENT");
  const link=page.getByRole("link",{name:/Зустріч/});
  await expect.element(link).toHaveAttribute("href","/parent/messages?messageId=9");
  await link.click();
  await expect.poll(()=>router.state.location.search).toBe("?messageId=9");
});

test("teacher navigation includes homeroom class and a profile path",async()=>{
  render(<><TeacherNavigation activeRoute="teacher/my-class" /><TeacherNavigation activeRoute="teacher/my-class" compact /><CurrentUserBadge /></>,"TEACHER");
  await expect.element(page.getByRole("link",{name:"Відкрити профіль"})).toHaveAttribute("href","/teacher/profile");
  await expect.poll(()=>container.querySelectorAll('a[href="/teacher/my-class"]').length).toBe(2);
  expect([...container.querySelectorAll('a')].every(a=>!a.getAttribute('href').includes('#'))).toBe(true);
});

test("teacher pages with no assigned classes still offer working logout",async()=>{
  vi.spyOn(teacherClassApi,"findAll").mockResolvedValue([]);
  vi.spyOn(userNotificationApi,"feed").mockResolvedValue({items:[],unreadCount:0});
  const logout=vi.spyOn(authApi,"logout").mockResolvedValue(null);
  render(<TeacherClassPage />,"TEACHER");
  await page.getByRole("button",{name:"Вийти",exact:true}).click();
  expect(logout).toHaveBeenCalledOnce();
  await expect.poll(()=>router.state.location.pathname).toBe("/login");
});

test("a parent can open a teacher conversation for their selected child",async()=>{
  vi.spyOn(parentApi,"findChildren").mockResolvedValue([{studentId:3,fullName:"Учень Тест",className:"9-А"}]);
  vi.spyOn(learnerTeachersApi,"parent").mockResolvedValue({className:"9-А",homeroomTeacher:null,teachers:[{id:7,userId:20,fullName:"Учитель Тест",email:"teacher@example.com",subjects:[{id:1,name:"Математика"}]}]});
  vi.spyOn(userNotificationApi,"feed").mockResolvedValue({items:[],unreadCount:0});
  render(<UserTeachersPage role="PARENT" />,"PARENT");
  await expect.element(page.getByRole("heading",{name:"Учитель Тест"})).toBeVisible();
  await page.getByRole("link",{name:"Написати повідомлення"}).click();
  await expect.poll(()=>router.state.location.pathname).toBe("/parent/messages");
  expect(new URLSearchParams(router.state.location.search).get("recipientId")).toBe("20");
});

test("a parent with no linked children can open the linking screen",async()=>{
  vi.spyOn(parentApi,"findChildren").mockResolvedValue([]);
  vi.spyOn(userNotificationApi,"feed").mockResolvedValue({items:[],unreadCount:0});
  render(<UserTeachersPage role="PARENT" />,"PARENT");
  await page.getByRole("link",{name:"Додати дитину",exact:true}).click();
  await expect.poll(()=>router.state.location.pathname+router.state.location.search).toBe("/parent/profile?addChild=1");
});

test("student contact details retain mail and phone links",async()=>{
  render(<StudentLearningHeader student={{fullName:"Учень Тест",login:"student"}} className="9-А"
    parents={[{id:1,fullName:"Батько Тест",email:"parent@example.com",phone:"+380671234567"}]} />,"TEACHER");
  await page.getByText("Контакти батьків",{exact:false}).click();
  await expect.element(page.getByRole("link",{name:"parent@example.com"})).toHaveAttribute("href","mailto:parent@example.com");
  await expect.element(page.getByRole("link",{name:"+380671234567"})).toHaveAttribute("href","tel:+380671234567");
});

test("an empty teacher roster can be exited even when the session already expired",async()=>{
  vi.spyOn(teacherStudentsApi,"find").mockResolvedValue([]);
  vi.spyOn(userNotificationApi,"feed").mockResolvedValue({items:[],unreadCount:0});
  vi.spyOn(authApi,"logout").mockRejectedValue(new Error("Session expired"));
  render(<TeacherStudentsPage />,"TEACHER");
  await page.getByRole("button",{name:"Вийти",exact:true}).click();
  await expect.poll(()=>router.state.location.pathname).toBe("/login");
});
