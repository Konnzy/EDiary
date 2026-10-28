import { useCallback, useEffect, useId, useRef, useState } from "react";
import { ArrowLeft, ChevronLeft, ChevronRight, Inbox, Loader2, PenLine, RefreshCw, Search, Send, Star } from "lucide-react";
import { Alert } from "@/components/ui/alert.jsx";
import { messagesApi } from "@/lib/api.js";
import { newRequestId } from "@/lib/diagnostics.js";
import { cn } from "@/lib/utils.js";

const tabs = [{id:"inbox",label:"Вхідні",icon:Inbox}, {id:"sent",label:"Вихідні",icon:Send}, {id:"important",label:"Важливі",icon:Star}, {id:"compose",label:"Нове повідомлення",shortLabel:"Нове",icon:PenLine}];
const roleLabels = {STUDENT:"Учень", PARENT:"Батьки", TEACHER:"Вчитель"};
const fieldClasses = "block h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-[0.82rem] text-slate-800 outline-hidden placeholder:text-slate-400 focus:border-slate-500 focus:ring-2 focus:ring-slate-200 disabled:opacity-60";
const commandClasses = "inline-flex h-9 items-center justify-center gap-2 rounded-md border border-slate-200 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40";
function changed() { window.dispatchEvent(new Event("messages-updated")); }
function dateLabel(date) { return new Intl.DateTimeFormat("uk-UA", {day:"2-digit",month:"2-digit",year:"numeric",hour:"2-digit",minute:"2-digit"}).format(new Date(date)); }
function messageCount(count) {
  const form=new Intl.PluralRules("uk-UA").select(count);
  return `${count} ${{one:"повідомлення",few:"повідомлення",many:"повідомлень",other:"повідомлення"}[form]}`;
}

export function Mailbox({initialRecipient="",initialRecipientId="",initialMessageId=""}) {
  const [tab,setTab]=useState(initialRecipient||initialRecipientId?"compose":"inbox");
  const [page,setPage]=useState(0), [mailbox,setMailbox]=useState(null), [loading,setLoading]=useState(true);
  const [error,setError]=useState(""), [notice,setNotice]=useState("");
  const [detail,setDetail]=useState(null), [opening,setOpening]=useState(false), [starring,setStarring]=useState(false);
  const [compose,setCompose]=useState({name:initialRecipient,id:initialRecipientId,key:0});
  const [reload,setReload]=useState(0);
  const openSequence=useRef(0);
  const refresh=useCallback(()=>setReload(value=>value+1),[]);
  useEffect(()=>{
    if(tab==="compose")return;
    let active=true;setLoading(true);setError("");
    messagesApi.list(tab,page).then(data=>{
      if(!active)return;
      if(page>0 && data.messages.content.length===0){setPage(0);return;}
      setMailbox(data);
    }).catch(e=>{if(active)setError(e.message);}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[tab,page,reload]);
  useEffect(()=>{
    const interval=setInterval(refresh,60000);
    window.addEventListener("messages-updated",refresh);
    return()=>{clearInterval(interval);window.removeEventListener("messages-updated",refresh);};
  },[refresh]);
  const open=useCallback(async id=>{
    const sequence=++openSequence.current;setOpening(true);setError("");
    try {const item=await messagesApi.open(id);if(sequence===openSequence.current){setDetail(item);refresh();changed();}}
    catch(e){if(sequence===openSequence.current)setError(e.message);}
    finally{if(sequence===openSequence.current)setOpening(false);}
  },[refresh]);
  useEffect(()=>{if(initialMessageId && /^\d+$/.test(initialMessageId))void open(initialMessageId);},[initialMessageId,open]);
  const selectTab=value=>{++openSequence.current;setOpening(false);setTab(value);setPage(0);setDetail(null);setError("");setNotice("");};
  const important=async item=>{
    const sequence=openSequence.current;
    setStarring(true);setError("");
    try {const updated=await messagesApi.important(item.id,!item.important);if(sequence===openSequence.current)setDetail(updated);refresh();}
    catch(e){setError(e.message);}finally{setStarring(false);}
  };
  const reply=()=>{setCompose({id:detail.sender.id,name:detail.sender.fullName,key:compose.key+1});selectTab("compose");};
  return <section aria-label="Повідомлення" className="rounded-lg border border-slate-200 bg-white shadow-[0_12px_36px_-30px_rgba(15,23,42,0.45)]">
    <div role="tablist" aria-label="Розділи повідомлень" className="grid grid-cols-4 border-b border-slate-200 px-2 sm:flex sm:gap-1 sm:px-4">
      {tabs.map((item,index)=>{const Icon=item.icon;return <button type="button" key={item.id} role="tab" id={`messages-tab-${item.id}`} aria-selected={tab===item.id} aria-controls="messages-panel" tabIndex={tab===item.id?0:-1}
        onClick={()=>selectTab(item.id)} onKeyDown={event=>{
          const next=event.key==="ArrowRight"?(index+1)%4:event.key==="ArrowLeft"?(index+3)%4:event.key==="Home"?0:event.key==="End"?3:-1;
          if(next>=0){event.preventDefault();selectTab(tabs[next].id);document.getElementById(`messages-tab-${tabs[next].id}`)?.focus();}
        }} className={cn("flex min-w-0 items-center justify-center gap-1.5 border-b-2 px-1 py-3 text-[0.72rem] font-semibold sm:px-4 sm:text-[0.82rem]",tab===item.id?"border-slate-800 text-slate-900":"border-transparent text-slate-500 hover:text-slate-800")}>
        <Icon className="h-4 w-4 shrink-0" aria-hidden="true" /><span className="sm:hidden">{item.shortLabel||item.label}</span><span className="hidden sm:inline">{item.label}</span>
        {item.id==="inbox" && mailbox?.unreadCount>0?<span className="hidden text-[0.65rem] tabular-nums sm:inline">({mailbox.unreadCount})</span>:null}
      </button>;})}
    </div>
    <div id="messages-panel" role="tabpanel" aria-labelledby={`messages-tab-${tab}`}>
      {error?<div className="p-4"><Alert>{error}</Alert><button className={cn(commandClasses,"mt-2")} onClick={refresh} type="button"><RefreshCw className="h-4 w-4" />Повторити</button></div>:null}
      {notice?<p className="px-5 pt-4 text-sm text-emerald-700" role="status">{notice}</p>:null}
      <div hidden={tab!=="compose"}><ComposeForm key={compose.key} initialName={compose.name} initialId={compose.id} onSent={()=>{setCompose({id:"",name:"",key:compose.key+1});selectTab("sent");setNotice("Повідомлення надіслано.");changed();}} /></div>
      {tab!=="compose" ? <>
          <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 text-xs text-slate-500"><span>{messageCount(mailbox?.messages.totalElements??0)}</span><button type="button" title="Оновити" aria-label="Оновити повідомлення" onClick={refresh} className="flex h-8 w-8 items-center justify-center rounded-md hover:bg-slate-50"><RefreshCw className={cn("h-4 w-4",loading&&"animate-spin")} /></button></div>
          <div className="grid min-h-[380px] md:grid-cols-[minmax(240px,340px)_minmax(0,1fr)]">
            <div className={cn("min-w-0 md:border-r md:border-slate-200",(detail||opening)&&"hidden md:block")}>
              {loading?<div className="flex justify-center p-12"><Loader2 aria-label="Завантаження повідомлень" className="h-5 w-5 animate-spin text-slate-400" /></div>
                : !error && mailbox?.messages.content.length===0?<p className="px-5 py-12 text-center text-sm text-slate-500">{tab==="inbox"?"Вхідних повідомлень поки немає":tab==="sent"?"Вихідних повідомлень поки немає":"Важливих повідомлень поки немає"}</p>
                  : <ul className="max-h-[640px] divide-y divide-slate-100 overflow-y-auto">{mailbox?.messages.content.map(item=><li key={item.id}><button type="button" onClick={()=>open(item.id)} className={cn("block w-full min-w-0 px-4 py-4 text-left hover:bg-slate-50",detail?.id===item.id&&"bg-slate-50")}>
                    <div className="flex items-center gap-2"><p className={cn("min-w-0 flex-1 truncate text-[0.82rem] text-slate-800",!item.read&&item.incoming?"font-bold":"font-medium")}>{item.subject}</p>{item.important&&item.incoming?<Star className="h-3.5 w-3.5 shrink-0 fill-amber-400 text-amber-500" aria-label="Важливе" />:null}{!item.read&&item.incoming?<span className="h-2 w-2 shrink-0 rounded-full bg-sky-600" aria-label="Непрочитане" />:null}</div>
                    <p className="mt-1 truncate text-xs text-slate-500">{(item.incoming?item.sender:item.receiver).fullName}</p><time className="mt-1 block text-[0.68rem] text-slate-400">{dateLabel(item.createdAt)}</time>
                  </button></li>)}</ul>}
              {mailbox?.messages.totalPages>1?<nav aria-label="Сторінки повідомлень" className="flex items-center justify-between border-t border-slate-200 p-3 text-xs text-slate-500"><button className={commandClasses} title="Попередня сторінка" aria-label="Попередня сторінка" disabled={page===0||loading} onClick={()=>setPage(page-1)}><ChevronLeft className="h-4 w-4" /></button><span>{page+1} / {mailbox.messages.totalPages}</span><button className={commandClasses} title="Наступна сторінка" aria-label="Наступна сторінка" disabled={mailbox.messages.last||loading} onClick={()=>setPage(page+1)}><ChevronRight className="h-4 w-4" /></button></nav>:null}
            </div>
            <div className={cn("min-w-0 p-4 sm:p-6",!detail&&!opening&&"hidden md:block")}>
              {opening?<Loader2 className="mx-auto mt-12 h-5 w-5 animate-spin text-slate-400" aria-label="Відкриття повідомлення" />:detail?<article>
                <div className="mb-4 flex flex-wrap items-center gap-2"><button type="button" className={cn(commandClasses,"md:hidden")} onClick={()=>{++openSequence.current;setDetail(null);}}><ArrowLeft className="h-4 w-4" />До списку</button>
                  {detail.incoming?<><button type="button" disabled={starring} className={commandClasses} onClick={()=>important(detail)} aria-pressed={detail.important} title={detail.important?"Прибрати з важливих":"Позначити важливим"} aria-label={detail.important?"Прибрати з важливих":"Позначити важливим"}><Star className={cn("h-4 w-4",detail.important&&"fill-amber-400 text-amber-500")} /></button><button type="button" className={commandClasses} onClick={reply}><PenLine className="h-4 w-4" />Відповісти</button></>:null}
                </div>
                <h2 className="break-words text-lg font-bold leading-6 text-slate-900">{detail.subject}</h2>
                <dl className="my-4 space-y-1 border-b border-slate-200 pb-4 text-xs text-slate-500"><div className="break-words"><dt className="inline">Від: </dt><dd className="inline font-semibold text-slate-700">{detail.sender.fullName}</dd></div><div className="break-words"><dt className="inline">Кому: </dt><dd className="inline font-semibold text-slate-700">{detail.receiver.fullName}</dd></div><div>{dateLabel(detail.createdAt)}</div>{!detail.incoming?<div>{detail.read?"Прочитано одержувачем":"Ще не прочитано"}</div>:null}</dl>
                <p className="whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">{detail.text}</p>
              </article>:<p className="py-24 text-center text-sm text-slate-400">Оберіть повідомлення</p>}
            </div>
          </div>
        </> : null}
    </div>
  </section>;
}

function ComposeForm({initialName,initialId,onSent}) {
  const [recipient,setRecipient]=useState(null),[subject,setSubject]=useState(""),[text,setText]=useState("");
  const [busy,setBusy]=useState(false),[error,setError]=useState("");
  const requestId=useRef(newRequestId());
  const edited=()=>{requestId.current=newRequestId();setError("");};
  const submit=async event=>{
    event.preventDefault();if(busy)return;
    if(!recipient){setError("Оберіть одержувача зі списку.");return;}
    setBusy(true);setError("");
    try{await messagesApi.send({recipientId:recipient.id,subject:subject.trim(),text:text.trim(),clientRequestId:requestId.current});onSent();}
    catch(e){setError(e.message);}finally{setBusy(false);}
  };
  return <form onSubmit={submit} className="max-w-[760px] space-y-5 p-4 sm:p-6">
    {error?<Alert>{error}</Alert>:null}
    <RecipientPicker initialName={initialName} initialId={initialId} disabled={busy} onChange={value=>{setRecipient(value);edited();}} />
    <Field label="Тема повідомлення" htmlFor="message-subject"><input id="message-subject" required maxLength={150} disabled={busy} value={subject} onChange={e=>{setSubject(e.target.value);edited();}} className={fieldClasses} /></Field>
    <Field label="Текст повідомлення" htmlFor="message-text"><textarea id="message-text" required maxLength={1000} disabled={busy} value={text} onChange={e=>{setText(e.target.value);edited();}} className={cn(fieldClasses,"min-h-[180px] resize-y py-3 leading-5")} /></Field>
    <div className="flex justify-end border-t border-slate-200 pt-4"><button type="submit" disabled={busy||!recipient||!subject.trim()||!text.trim()} className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-800 px-4 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-50">{busy?<Loader2 className="h-4 w-4 animate-spin" />:<Send className="h-4 w-4" />}Надіслати</button></div>
  </form>;
}

function RecipientPicker({initialName,initialId,disabled,onChange}) {
  const [query,setQuery]=useState(initialName),[open,setOpen]=useState(false),[options,setOptions]=useState([]),[loading,setLoading]=useState(false),[error,setError]=useState(""),[active,setActive]=useState(0);
  const change=useRef(onChange);change.current=onChange;
  const revision=useRef(0);
  const listId=useId();
  useEffect(()=>{if(open)document.getElementById(`${listId}-${active}`)?.scrollIntoView({block:"nearest"});},[open,active,listId]);
  useEffect(()=>{
    if(!initialId)return;
    let current=true;const version=revision.current;setLoading(true);
    messagesApi.recipients("",initialId).then(items=>{if(!current||version!==revision.current)return;if(items[0]){setQuery(items[0].fullName);change.current(items[0]);}else setError("Одержувач більше недоступний. Оберіть іншого.");}).catch(e=>{if(current&&version===revision.current)setError(e.message);}).finally(()=>{if(current&&version===revision.current)setLoading(false);});
    return()=>{current=false;};
  },[initialId]);
  useEffect(()=>{
    if(!open)return;
    let current=true;setLoading(true);setError("");setOptions([]);
    const timer=setTimeout(()=>messagesApi.recipients(query).then(items=>{if(current){setOptions(items);setActive(0);}}).catch(e=>{if(current)setError(e.message);}).finally(()=>{if(current)setLoading(false);}),250);
    return()=>{current=false;clearTimeout(timer);};
  },[query,open]);
  const select=item=>{revision.current++;setQuery(item.fullName);setOpen(false);change.current(item);};
  return <Field label="ПІБ одержувача" htmlFor="message-recipient"><div className="relative" onBlur={event=>{if(!event.currentTarget.contains(event.relatedTarget))setOpen(false);}}>
    <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-slate-400" />
    <input id="message-recipient" role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={listId} aria-activedescendant={open&&options[active]?`${listId}-${active}`:undefined} autoComplete="off" maxLength={150} disabled={disabled} value={query} placeholder="Прізвище Ім’я По батькові" onFocus={()=>setOpen(true)} onChange={event=>{revision.current++;setQuery(event.target.value);setOpen(true);change.current(null);}} onKeyDown={event=>{
      if(event.key==="Escape"){setOpen(false);return;}
      if(event.key==="ArrowDown"||event.key==="ArrowUp"){event.preventDefault();setOpen(true);setActive(index=>Math.max(0,Math.min(options.length-1,index+(event.key==="ArrowDown"?1:-1))));}
      if(event.key==="Enter"&&open){event.preventDefault();if(options[active])select(options[active]);}
    }} className={cn(fieldClasses,"pl-9")} />
    {open?<ul id={listId} role="listbox" aria-label="Одержувачі" className="absolute inset-x-0 top-full z-20 mt-1 max-h-64 overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg">
      {loading?<li role="presentation" className="p-3 text-xs text-slate-500">Пошук…</li>:options.map((item,index)=><li key={item.id} role="presentation"><button type="button" id={`${listId}-${index}`} role="option" aria-selected={index===active} onMouseDown={e=>e.preventDefault()} onClick={()=>select(item)} className={cn("block w-full break-words px-3 py-2 text-left text-sm",index===active?"bg-slate-100":"hover:bg-slate-50")}><span className="font-semibold">{item.fullName}</span><span className="ml-2 text-xs text-slate-500">{item.homeroom ? "Класний керівник" : roleLabels[item.role]}</span></button></li>)}
      {!loading&&!options.length?<li role="presentation" className="p-3 text-xs text-slate-500">{error||"Одержувачів не знайдено"}</li>:null}
    </ul>:null}
    {!open&&error?<p className="mt-2 text-xs text-red-600" role="alert">{error}</p>:null}
  </div></Field>;
}
function Field({label,htmlFor,children}) { return <div className="space-y-2"><label htmlFor={htmlFor} className="block text-[0.78rem] font-semibold text-slate-700">{label}</label>{children}</div>; }
