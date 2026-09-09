import { act, render, renderHook, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { ConfirmProvider, useConfirm } from './confirm-dialog'
import { useState } from 'react'
function Example() {
 const confirm=useConfirm(); const [results,setResults]=useState<boolean[]>([])
 return <><button onClick={async()=>{const result=await confirm({title:'Notiz löschen',message:'Notiz wirklich löschen?'});setResults(v=>[...v,result])}}>Löschen</button><output>{JSON.stringify(results)}</output></>
}
it('bestätigt einmal und gibt den Fokus zurück; Abbrechen und Escape liefern false',async()=>{
 const user=userEvent.setup();render(<ConfirmProvider><Example/></ConfirmProvider>)
 const trigger=screen.getByText('Löschen')
 await user.click(trigger);expect(screen.getByRole('button',{name:'Abbrechen'})).toHaveFocus()
 await user.keyboard('{Shift>}{Tab}{/Shift}');expect(screen.getByRole('button',{name:'Bestätigen'})).toHaveFocus()
 await user.click(screen.getByText('Bestätigen'))
 expect(screen.getByRole('status')).toHaveTextContent('[true]');expect(trigger).toHaveFocus()
 await user.click(trigger);await user.keyboard('{Escape}')
 expect(screen.getByRole('status')).toHaveTextContent('[true,false]')
 await user.click(trigger);await user.click(screen.getByText('Abbrechen'))
 expect(screen.getByRole('status')).toHaveTextContent('[true,false,false]')
})
it('löst ausstehende und konkurrierende Bestätigungen sicher auf',async()=>{
 const {result,unmount}=renderHook(useConfirm,{wrapper:ConfirmProvider})
 const confirm=result.current
 let first!:Promise<boolean>;let second!:Promise<boolean>
 act(()=>{first=confirm({message:'Erste'});second=confirm({message:'Zweite'})})
 await expect(first).resolves.toBe(false)
 unmount();await expect(second).resolves.toBe(false)
})
